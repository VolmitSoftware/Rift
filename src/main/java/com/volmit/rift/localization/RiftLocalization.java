package com.volmit.rift.localization;

import art.arcane.volmlib.util.localization.LanguageFileHeader;

import art.arcane.volmlib.util.director.DirectorTextResolver;
import art.arcane.volmlib.util.format.ColorFormatter;
import art.arcane.volmlib.util.io.AtomicFileIO;
import art.arcane.volmlib.util.localization.LanguageReferenceRenderer;
import art.arcane.volmlib.util.localization.LocaleOverlay;
import art.arcane.volmlib.util.localization.LocalizationCandidate;
import art.arcane.volmlib.util.localization.LocalizationManager;
import art.arcane.volmlib.util.localization.LocalizationSnapshot;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.MessageArgumentKind;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.MessageValue;
import art.arcane.volmlib.util.localization.PluginLanguageEditor;
import art.arcane.volmlib.util.localization.PluginLanguageService;
import art.arcane.volmlib.util.localization.PluralSelector;
import art.arcane.volmlib.util.localization.RemoteLanguageCatalog;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.localization.TextValue;
import art.arcane.volmlib.util.localization.TomlLanguageEditor;
import art.arcane.volmlib.util.localization.TomlLanguageParser;
import art.arcane.volmlib.util.localization.VolmitLocales;
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import art.arcane.volmlib.util.plugin.ComponentText;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.volmit.rift.config.RiftConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class RiftLocalization implements AutoCloseable {
    private static final long MAXIMUM_LANGUAGE_BYTES = 2L * 1024L * 1024L;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final MiniMessage STRICT_MINI_MESSAGE = MiniMessage.builder().strict(true).build();
    private static final PluralSelector ENGLISH_PLURALS = PluralSelector.oneOther();
    private static final MessageCatalog CATALOG = RiftMessages.catalog();
    private static final Pattern LOCALE_PATTERN = Pattern.compile("[A-Za-z0-9_-]{2,32}");

    private final Plugin plugin;
    private final Supplier<RiftConfig> config;
    private final File directory;
    private final Path preferenceFile;
    private final LocalizationManager manager;
    private final AtomicReference<File> activeFile;
    private final AtomicReference<String> activeLocale;
    private final AtomicReference<String> remoteDownloadFailure = new AtomicReference<>("");
    private final RemoteLanguageCatalog remoteCatalog;
    private final Throwable remoteCatalogFailure;
    private final Set<String> announcedDownloads = new HashSet<>();
    private volatile List<String> locales = List.of();
    private volatile BiConsumer<File, String> selfWriteListener;
    private volatile PluginLanguageService selections;

    public RiftLocalization(Plugin plugin, Supplier<RiftConfig> config, File directory) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.config = Objects.requireNonNull(config, "config");
        this.directory = Objects.requireNonNull(directory, "directory");
        preferenceFile = directory.toPath().resolve("language-preferences.properties");
        validateCatalogTemplates();
        manager = new LocalizationManager(LocalizationCandidate.english(CATALOG, ENGLISH_PLURALS));
        activeFile = new AtomicReference<>(file(VolmitLocales.ENGLISH));
        activeLocale = new AtomicReference<>(VolmitLocales.ENGLISH);
        RemoteLanguageCatalog loadedCatalog = null;
        Throwable catalogFailure = null;
        try {
            loadedCatalog = RemoteLanguageCatalog.load(new RemoteLanguageCatalog.Options(
                    "Rift",
                    URI.create("https://raw.githubusercontent.com/VolmitSoftware/Rift/"),
                    "src/main/resources/languages",
                    ".toml",
                    "rift-language-source.properties",
                    RiftLocalization.class.getClassLoader()
            ));
        } catch (Throwable failure) {
            catalogFailure = failure;
        }
        remoteCatalog = loadedCatalog;
        remoteCatalogFailure = catalogFailure;
    }

    public boolean loadInitial() {
        String locale = config.get().getLanguage();
        try {
            PreparedLanguage prepared = prepare(locale);
            if (prepared.selectionReady()) {
                install(prepared);
                return true;
            }
            install(englishFallback(locale));
            requestRemote(locale, this::initialDownloadCompleted);
            return true;
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Unable to establish Rift language " + locale + " from " + file(locale), exception);
            return false;
        }
    }

    public synchronized boolean reload() {
        String locale = config.get().getLanguage();
        try {
            PreparedLanguage prepared = prepare(locale);
            if (!prepared.selectionReady()) {
                plugin.getLogger().warning("Rift language " + locale
                        + " is not installed; the last valid language remains active");
                return false;
            }
            install(prepared);
            return true;
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Unable to load Rift language " + locale + " from " + file(locale), exception);
            return false;
        }
    }

    public synchronized boolean reloadSnapshot(File source, String raw) {
        if (!isLanguageFile(source)) {
            return false;
        }
        try {
            String locale = canonicalLocale(locale(source.toPath()).orElseThrow());
            PreparedLanguage prepared;
            if (raw == null) {
                if (!Files.notExists(source.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Language snapshot is unreadable: " + source);
                }
                prepared = new PreparedLanguage(locale, source,
                        LocalizationSnapshot.create(LocalizationCandidate.english(CATALOG, ENGLISH_PLURALS)), false);
            } else {
                prepared = prepareSnapshot(locale, raw);
            }
            if (sameLocale(locale, config.get().getLanguage())) {
                install(prepared);
            } else if (selections != null) {
                selections.cache(locale, selectionSnapshot(locale, prepared.snapshot()));
            }
            return true;
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Rejected invalid language snapshot for " + source
                            + "; the last valid language remains active",
                    exception);
            return false;
        }
    }

    public synchronized PreparedLanguage prepare(String requestedLocale) throws IOException {
        String locale = canonicalLocale(requestedLocale);
        prepareLanguageDirectory();
        createEnglishLanguageIfMissing();
        File target = file(locale);
        requireRegularLanguageFile(target, true);
        if (!target.exists() && !isRepositoryLocale(locale)) {
            createCustomLanguageIfMissing(locale);
        }
        ArrayList<LocaleOverlay> overlays = new ArrayList<>();
        if (target.isFile()) {
            try {
                overlays.add(createOverlay(locale, target.getPath(), loadEditableLanguage(target)));
            } catch (IOException | RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Using English for unreadable language file " + target, exception);
            }
        }
        PreparedLanguage prepared = createPrepared(locale, target, overlays, target.isFile());
        refreshAvailableLocales();
        return prepared;
    }

    public synchronized PreparedLanguage prepareSnapshot(String requestedLocale, String raw) throws IOException {
        if (raw == null) {
            throw new IOException("Language snapshot is missing");
        }
        String locale = canonicalLocale(requestedLocale);
        File target = file(locale);
        requireRegularLanguageFile(target, false);
        Map<String, String> values = parseValues(raw, locale);
        LocaleOverlay overlay = createOverlay(locale, target.getPath(), values);
        return createPrepared(locale, target, List.of(overlay), true);
    }

    public synchronized PreparedLanguage englishFallback(String locale) throws IOException {
        String requiredLocale = canonicalLocale(locale);
        prepareLanguageDirectory();
        createEnglishLanguageIfMissing();
        refreshAvailableLocales();
        return new PreparedLanguage(
                requiredLocale,
                file(requiredLocale),
                LocalizationSnapshot.create(LocalizationCandidate.english(CATALOG, ENGLISH_PLURALS)),
                requiredLocale.equalsIgnoreCase(CATALOG.englishLocale())
        );
    }

    public void install(PreparedLanguage prepared) {
        PreparedLanguage required = Objects.requireNonNull(prepared, "prepared");
        manager.install(required.snapshot());
        activeFile.set(required.file());
        activeLocale.set(required.locale());
        PluginLanguageService activeSelections = selections;
        if (activeSelections != null) {
            activeSelections.cache(required.locale(), selectionSnapshot(required.locale(), required.snapshot()));
        }
    }

    public synchronized PluginLanguageService initializeSelections(
            Supplier<String> defaultLocale,
            PluginLanguageService.DefaultSelection defaultSelection
    ) {
        if (selections != null) {
            return selections;
        }
        PluginLanguageService created = new PluginLanguageService(new PluginLanguageService.Options(
                preferenceFile,
                this::availableLocales,
                defaultLocale,
                manager::snapshot,
                this::loadSelectionSnapshot,
                defaultSelection,
                plugin.getLogger()
        ));
        created.cache(activeLocale(), selectionSnapshot(activeLocale(), manager.snapshot()));
        selections = created;
        return created;
    }

    public PluginLanguageService selections() {
        PluginLanguageService activeSelections = selections;
        if (activeSelections == null) {
            throw new IllegalStateException("Language selections are not initialized");
        }
        return activeSelections;
    }

    public PluginLanguageEditor.Options editorOptions() {
        return new PluginLanguageEditor.Options(this::loadSelectionSnapshot, this::saveEditorMessage);
    }

    public synchronized List<EditableMessage> editableMessages(String locale) throws IOException {
        return editableMessages(prepare(locale));
    }

    public synchronized List<EditableMessage> editableMessages(PreparedLanguage prepared) throws IOException {
        PreparedLanguage required = Objects.requireNonNull(prepared, "prepared");
        if (!required.selectionReady()) {
            throw new IOException("Language file is not installed: " + required.locale());
        }
        ArrayList<MessageKey> definitions = new ArrayList<>(CATALOG.keys());
        definitions.sort(Comparator.comparing(MessageKey::id, String.CASE_INSENSITIVE_ORDER));
        ArrayList<EditableMessage> messages = new ArrayList<>(definitions.size());
        for (MessageKey definition : definitions) {
            if (!(definition instanceof TextKey textKey)) {
                throw new IOException("Language editor does not support key shape: " + definition.id());
            }
            MessageValue effectiveValue = required.snapshot().value(textKey);
            if (!(effectiveValue instanceof TextValue textValue)) {
                throw new IOException("Language editor resolved a non-text value: " + definition.id());
            }
            messages.add(new EditableMessage(
                    definition.id(),
                    textValue.template(),
                    previewValue(required.snapshot(), definition, textValue.template()),
                    definition.placeholders()
            ));
        }
        return List.copyOf(messages);
    }

    public synchronized PreparedLanguage updateMessage(String locale, String key, String value) throws IOException {
        String requiredLocale = canonicalLocale(locale);
        MessageKey definition = CATALOG.require(key);
        if (!(definition instanceof TextKey)) {
            throw new IOException("Language editor does not support key shape: " + definition.id());
        }
        try {
            validateTemplate("language:" + key, value, sampleArguments(definition.placeholders()));
            LocalizationSnapshot.create(new LocalizationCandidate(CATALOG,
                    List.of(LocaleOverlay.builder("editor", requiredLocale).text(key, value).build()), ENGLISH_PLURALS));
        } catch (RuntimeException exception) {
            throw new IOException("Language file contains invalid message markup: " + key, exception);
        }
        PreparedLanguage current = prepare(requiredLocale);
        if (!current.selectionReady()) {
            throw new IOException("Language file is not installed: " + requiredLocale);
        }
        File target = file(requiredLocale);
        FileSource source = readFileSource(target);
        TomlLanguageEditor.EditResult edit = TomlLanguageEditor.upsertText(source.content(), definition.id(), value);
        String content = edit.content();
        if (content.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_LANGUAGE_BYTES) {
            throw new IOException("Language file exceeds the 2 MiB safety limit");
        }
        Map<String, String> values = TomlLanguageParser.parseValidText(content, CATALOG);
        LocaleOverlay overlay = createOverlay(requiredLocale, target.getPath(), values);
        PreparedLanguage prepared = createPrepared(requiredLocale, target, List.of(overlay), true);
        verifyUnchanged(target, source);
        AtomicFileIO.writeString(target.toPath(), content);
        refreshAvailableLocales();
        BiConsumer<File, String> listener = selfWriteListener;
        if (listener != null) {
            listener.accept(target, content);
        }
        PluginLanguageService activeSelections = selections;
        if (activeSelections != null) {
            LocalizationSnapshot selected = selectionSnapshot(requiredLocale, prepared.snapshot());
            activeSelections.cache(requiredLocale, selected);
            if (sameLocale(requiredLocale, activeSelections.defaultLocale())) {
                manager.install(prepared.snapshot());
                activeFile.set(prepared.file());
                activeLocale.set(prepared.locale());
            }
        }
        return prepared;
    }

    public void setSelfWriteListener(BiConsumer<File, String> listener) {
        selfWriteListener = listener;
    }

    public File file() {
        return activeFile.get();
    }

    public File file(String locale) {
        return new File(directory, requireLocale(locale) + ".toml");
    }

    public File directory() {
        return directory;
    }

    public List<File> files() {
        Path root = directory.toPath().toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(root)) {
            return stream
                    .filter(path -> locale(path).isPresent())
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .map(Path::toFile)
                    .toList();
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Unable to enumerate Rift language files", exception);
            return List.of();
        }
    }

    public boolean isLanguageFile(File candidate) {
        if (candidate == null || candidate.getParentFile() == null) {
            return false;
        }
        Path parent = candidate.getParentFile().toPath().toAbsolutePath().normalize();
        Path expected = directory.toPath().toAbsolutePath().normalize();
        if (!expected.equals(parent)) {
            return false;
        }
        return locale(candidate.toPath()).isPresent();
    }

    public String activeLocale() {
        return activeLocale.get();
    }

    public List<String> availableLocales() {
        return locales;
    }

    public synchronized void refreshAvailableLocales() {
        Path root = directory.toPath().toAbsolutePath().normalize();
        LinkedHashSet<String> choices = new LinkedHashSet<>();
        choices.add(CATALOG.englishLocale());
        if (remoteCatalog != null) {
            choices.addAll(remoteCatalog.availableLocales());
        }
        if (Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(root)) {
            try (Stream<Path> stream = Files.list(root)) {
                for (Path path : stream.toList()) {
                    locale(path).ifPresent(choices::add);
                }
            } catch (IOException exception) {
                plugin.getLogger().log(Level.WARNING, "Unable to enumerate Rift languages in " + directory, exception);
            }
        }
        ArrayList<String> discovered = new ArrayList<>(choices);
        discovered.sort(String.CASE_INSENSITIVE_ORDER);
        locales = List.copyOf(discovered);
    }

    public Optional<String> availableLocale(String requested) {
        String normalized;
        try {
            normalized = requireLocale(requested);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
        return locales.stream()
                .filter(locale -> locale.equalsIgnoreCase(normalized))
                .findFirst();
    }

    public String localeDisplayName(String locale) {
        String normalized = locale == null ? "" : locale.trim();
        return VolmitLocales.displayName(normalized).orElse("Custom language");
    }

    public Optional<Throwable> remoteCatalogFailure() {
        return Optional.ofNullable(remoteCatalogFailure);
    }

    public Optional<String> remoteCatalogReference() {
        return remoteCatalog == null ? Optional.empty() : Optional.of(remoteCatalog.revision());
    }

    public Optional<String> remoteDownloadFailure() {
        String failure = remoteDownloadFailure.get();
        return failure.isBlank() ? Optional.empty() : Optional.of(failure);
    }

    public boolean hasRemoteCatalogLocale(String locale) {
        String requiredLocale = canonicalLocale(locale);
        return remoteCatalog != null && remoteCatalog.availableLocales().contains(requiredLocale);
    }

    public synchronized RemoteLanguageCatalog.RequestState requestRemote(
            String locale,
            Consumer<RemoteLanguageCatalog.DownloadResult> completion
    ) {
        if (remoteCatalog == null) {
            return RemoteLanguageCatalog.RequestState.CLOSED;
        }
        String requiredLocale = canonicalLocale(locale);
        File target = file(requiredLocale);
        RemoteLanguageCatalog.RequestState request = remoteCatalog.requestInstallIfMissing(
                requiredLocale,
                target.toPath(),
                this::validateDownloadedContent,
                result -> remoteInstallCompleted(target, result, completion)
        );
        if (request == RemoteLanguageCatalog.RequestState.SCHEDULED) {
            announcedDownloads.add(requiredLocale);
            plugin.getLogger().info("Downloading Rift language " + requiredLocale + " from "
                    + remoteCatalog.sourceUri(requiredLocale) + "...");
        }
        return request;
    }

    public ComponentText text(TextKey key) {
        return text(key, MessageArgs.empty());
    }

    public ComponentText text(TextKey key, MessageArgs arguments) {
        LocalizationSnapshot snapshot = selectedSnapshot(null);
        return render(snapshot, key, arguments, false);
    }

    public ComponentText text(CommandSender sender, TextKey key) {
        return text(sender, key, MessageArgs.empty());
    }

    public ComponentText text(CommandSender sender, TextKey key, MessageArgs arguments) {
        LocalizationSnapshot snapshot = selectedSnapshot(sender);
        return render(snapshot, key, arguments, false);
    }

    public ComponentText textWithoutPrefix(TextKey key, MessageArgs arguments) {
        return render(selectedSnapshot(null), key, arguments, true);
    }

    public ComponentText textWithoutPrefix(CommandSender sender, TextKey key, MessageArgs arguments) {
        return render(selectedSnapshot(sender), key, arguments, true);
    }

    public ComponentText prefix() {
        return ComponentText.markup(renderPrefix(selectedSnapshot(null)));
    }

    public ComponentText prefixed(ComponentText message) {
        return prefix().append(ComponentText.markup("&r &7› &7"))
                .append(Objects.requireNonNull(message, "message").colorIfAbsent("#AAAAAA"));
    }

    public void send(CommandSender sender, TextKey key) {
        send(sender, key, MessageArgs.empty());
    }

    public void send(CommandSender sender, TextKey key, MessageArgs arguments) {
        send(sender, text(sender, key, arguments));
    }

    public void sendPrefixed(CommandSender sender, ComponentText message) {
        send(sender, text(sender, RiftMessages.PREFIX).append(ComponentText.markup("&r &7› &7"))
                .append(Objects.requireNonNull(message, "message").colorIfAbsent("#AAAAAA")));
    }

    public DirectorTextResolver directorResolver() {
        return (key, arguments) -> {
            MessageKey definition = CATALOG.key(key.id());
            if (!(definition instanceof TextKey textKey)) {
                return DirectorTextResolver.ENGLISH.resolve(key, arguments);
            }
            return RiftMessages.isSharedChat(textKey.id()) || textKey.id().equals("language.editor.title")
                    || textKey.id().equals(RiftMessages.VERSION.id())
                    ? text(textKey, arguments).miniMessage()
                    : textWithoutPrefix(textKey, arguments).plain();
        };
    }

    public void send(CommandSender sender, ComponentText message) {
        if (sender instanceof Player player) {
            if (FoliaScheduler.isOwnedByCurrentRegion(player)) {
                ComponentMessenger.send(player, message);
                return;
            }
            if (!FoliaScheduler.runEntity(plugin, player, () -> ComponentMessenger.send(player, message))) {
                plugin.getLogger().warning("Unable to schedule a localized message for " + player.getName());
            }
            return;
        }
        if (FoliaScheduler.isPrimaryThread()) {
            ComponentMessenger.send(sender, message);
            return;
        }
        if (!FoliaScheduler.runGlobal(plugin, () -> ComponentMessenger.send(sender, message))) {
            plugin.getLogger().warning("Unable to schedule a localized message for " + sender.getName());
        }
    }

    @Override
    public void close() {
        selfWriteListener = null;
        PluginLanguageService activeSelections = selections;
        if (activeSelections != null) {
            activeSelections.close();
        }
        synchronized (this) {
            announcedDownloads.clear();
        }
        if (remoteCatalog != null) {
            remoteCatalog.close();
        }
    }

    static String referenceToml() {
        return LanguageReferenceRenderer.render(CATALOG, englishHeader(CATALOG.englishLocale()));
    }

    void validateDownloadedContent(String locale, String content) throws IOException {
        Set<String> expected = CATALOG.byId().keySet();
        Map<String, String> values = parseStrictValues(content, locale, expected);
        LocaleOverlay overlay = createOverlay(locale, "download:" + locale, values);
        try {
            LocalizationSnapshot.create(new LocalizationCandidate(CATALOG, List.of(overlay), ENGLISH_PLURALS));
        } catch (RuntimeException exception) {
            throw new IOException("Downloaded locale is invalid: " + locale, exception);
        }
    }

    private void initialDownloadCompleted(RemoteLanguageCatalog.DownloadResult result) {
        if (!result.successful()) {
            plugin.getLogger().log(Level.WARNING,
                    "Unable to download configured Rift language " + result.locale()
                            + "; built-in English remains active",
                    result.failure());
            return;
        }
        if (config.get().getLanguage().equalsIgnoreCase(result.locale()) && reload()) {
            plugin.getLogger().info("Activated Rift language " + result.locale() + " after download.");
        }
    }

    private void remoteInstallCompleted(
            File target,
            RemoteLanguageCatalog.DownloadResult result,
            Consumer<RemoteLanguageCatalog.DownloadResult> completion
    ) {
        RemoteLanguageCatalog.DownloadResult delivered = result;
        if (result.successful()) {
            try {
                String content = Files.readString(target.toPath(), StandardCharsets.UTF_8);
                BiConsumer<File, String> listener = selfWriteListener;
                if (listener != null) {
                    try {
                        listener.accept(target, content);
                    } catch (RuntimeException exception) {
                        plugin.getLogger().log(Level.WARNING,
                                "Downloaded Rift language " + result.locale()
                                        + " but failed to register the file with hot reload; continuing with activation",
                                exception);
                    }
                }
            } catch (IOException exception) {
                delivered = new RemoteLanguageCatalog.DownloadResult(
                        result.locale(), result.source(), result.file(), exception);
            }
        }
        if (delivered.successful()) {
            remoteDownloadFailure.set("");
        } else {
            Throwable failure = delivered.failure();
            remoteDownloadFailure.set(delivered.locale() + " from " + delivered.source() + " - "
                    + failure.getClass().getSimpleName() + ": "
                    + Objects.toString(failure.getMessage(), "no message"));
        }
        announceDownloadCompleted(delivered);
        completion.accept(delivered);
    }

    private synchronized void announceDownloadCompleted(RemoteLanguageCatalog.DownloadResult result) {
        if (!announcedDownloads.remove(result.locale()) || !result.successful()) {
            return;
        }
        plugin.getLogger().info("Downloaded Rift language " + result.locale() + " to "
                + result.file().toAbsolutePath().normalize() + ".");
    }

    private PreparedLanguage createPrepared(
            String locale,
            File file,
            List<LocaleOverlay> overlays,
            boolean selectionReady
    ) throws IOException {
        try {
            LocalizationSnapshot snapshot = LocalizationSnapshot.create(
                    new LocalizationCandidate(CATALOG, overlays, ENGLISH_PLURALS)
            );
            return new PreparedLanguage(locale, file, snapshot, selectionReady);
        } catch (RuntimeException exception) {
            throw new IOException("Invalid language selection " + locale, exception);
        }
    }

    private synchronized LocalizationSnapshot loadSelectionSnapshot(String locale) throws Exception {
        String requiredLocale = canonicalLocale(locale);
        prepareLanguageDirectory();
        createEnglishLanguageIfMissing();
        File target = file(requiredLocale);
        requireRegularLanguageFile(target, true);
        if (!target.exists() && hasRemoteCatalogLocale(requiredLocale)) {
            URI source = remoteCatalog.sourceUri(requiredLocale);
            plugin.getLogger().info("Downloading Rift language " + requiredLocale + " from " + source + "...");
            String content = remoteCatalog.readOrInstall(
                    requiredLocale,
                    target.toPath(),
                    this::validateDownloadedContent
            );
            noteSelfWrite(target, content);
            plugin.getLogger().info("Downloaded Rift language " + requiredLocale + " to "
                    + target.toPath().toAbsolutePath().normalize() + ".");
        } else if (!target.exists()) {
            createCustomLanguageIfMissing(requiredLocale);
        }
        PreparedLanguage prepared = prepare(requiredLocale);
        if (!prepared.selectionReady()) {
            throw new IOException("Language file is not installed: " + requiredLocale);
        }
        return selectionSnapshot(requiredLocale, prepared.snapshot());
    }

    private LocalizationSnapshot saveEditorMessage(PluginLanguageEditor.Edit edit) throws Exception {
        LocalizationSnapshot current = loadSelectionSnapshot(edit.locale());
        MessageKey definition = CATALOG.require(edit.key());
        if (!current.value(definition).equals(edit.expected())) {
            throw new IOException("Language message changed while it was being edited: " + edit.key());
        }
        if (!(edit.value() instanceof TextValue textValue)) {
            throw new IllegalArgumentException("Unsupported language message shape: " + edit.key());
        }
        return selectionSnapshot(edit.locale(), updateMessage(edit.locale(), edit.key(), textValue.template()).snapshot());
    }

    private LocalizationSnapshot selectionSnapshot(String locale, LocalizationSnapshot prepared) {
        if (sameLocale(locale, CATALOG.englishLocale())) {
            return prepared;
        }
        ArrayList<LocaleOverlay> overlays = new ArrayList<>(prepared.overlays());
        LocaleOverlay.Builder englishFallback = LocaleOverlay.builder("code-owned-English:" + locale, locale);
        for (MessageKey key : CATALOG.keys()) {
            englishFallback.put(key.id(), key.englishValue());
        }
        overlays.add(englishFallback.build());
        return LocalizationSnapshot.create(new LocalizationCandidate(CATALOG, overlays, ENGLISH_PLURALS));
    }

    private LocaleOverlay createOverlay(String locale, String source, Map<String, String> values) throws IOException {
        LocaleOverlay.Builder overlay = LocaleOverlay.builder(source, locale);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            MessageKey definition = CATALOG.key(entry.getKey());
            if (definition == null) {
                continue;
            }
            if (!(definition instanceof TextKey)) {
                throw new IOException("Language key is not text: " + entry.getKey());
            }
            try {
                validateTemplate("language:" + entry.getKey(), entry.getValue(),
                        sampleArguments(definition.placeholders()));
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Using English for invalid language message " + source + ":" + entry.getKey(), exception);
                continue;
            }
            overlay.text(entry.getKey(), entry.getValue());
        }
        return overlay.build();
    }

    private Map<String, String> loadEditableLanguage(File file) throws IOException {
        FileSource source = readFileSource(file);
        try {
            return TomlLanguageParser.parseValidText(source.content(), CATALOG);
        } catch (IOException exception) {
            throw new IOException("Invalid TOML in " + file.getName() + ": " + exception.getMessage(), exception);
        }
    }

    private Map<String, String> parseValues(String content, String locale) throws IOException {
        return parseStrictValues(content, locale, CATALOG.byId().keySet());
    }

    private Map<String, String> parseStrictValues(
            String content,
            String locale,
            Set<String> expected
    ) throws IOException {
        if (content.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_LANGUAGE_BYTES) {
            throw new IOException("Language file exceeds the 2 MiB safety limit");
        }
        try {
            return TomlLanguageParser.parseValidText(content, CATALOG);
        } catch (IOException exception) {
            throw new IOException("Invalid TOML in " + locale + ".toml: " + exception.getMessage(), exception);
        }
    }

    private FileSource readFileSource(File file) throws IOException {
        Path path = file.toPath().toAbsolutePath().normalize();
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return new FileSource(false, new byte[0], "");
        }
        requireRegularLanguageFile(file, false);
        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length > MAXIMUM_LANGUAGE_BYTES) {
            throw new IOException("Language file exceeds the 2 MiB safety limit");
        }
        try {
            return new FileSource(true, bytes, decodeUtf8(bytes));
        } catch (CharacterCodingException exception) {
            throw new IOException("Invalid UTF-8 in " + file.getName(), exception);
        }
    }

    private void verifyUnchanged(File file, FileSource expected) throws IOException {
        Path path = file.toPath().toAbsolutePath().normalize();
        boolean exists = Files.exists(path, LinkOption.NOFOLLOW_LINKS);
        byte[] current = exists ? Files.readAllBytes(path) : new byte[0];
        if (exists != expected.existed() || !Arrays.equals(expected.bytes(), current)) {
            throw new IOException("Language file changed while the editor was saving; try again");
        }
    }

    private String decodeUtf8(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    private void prepareLanguageDirectory() throws IOException {
        Path root = directory.toPath().toAbsolutePath().normalize();
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS)
                && (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root))) {
            throw new IOException("Rift languages path is not a regular directory: " + root);
        }
        Files.createDirectories(root);
    }

    private void createEnglishLanguageIfMissing() throws IOException {
        File english = file(CATALOG.englishLocale());
        if (english.exists()) {
            requireRegularLanguageFile(english, false);
            return;
        }
        String content = referenceToml();
        AtomicFileIO.writeString(english.toPath(), content);
        noteSelfWrite(english, content);
    }

    private void createCustomLanguageIfMissing(String locale) throws IOException {
        File target = file(locale);
        if (target.exists()) {
            requireRegularLanguageFile(target, false);
            return;
        }
        String content = LanguageReferenceRenderer.render(CATALOG, englishHeader(locale));
        AtomicFileIO.writeString(target.toPath(), content);
        noteSelfWrite(target, content);
    }

    private void noteSelfWrite(File file, String content) {
        BiConsumer<File, String> listener = selfWriteListener;
        if (listener != null) {
            listener.accept(file, content);
        }
    }

    private void requireRegularLanguageFile(File file, boolean allowMissing) throws IOException {
        Path path = file.toPath().toAbsolutePath().normalize();
        Path root = directory.toPath().toAbsolutePath().normalize();
        if (!root.equals(path.getParent())) {
            throw new IOException("Language file escapes the Rift languages directory: " + path);
        }
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            if (allowMissing) {
                return;
            }
            throw new IOException("Language file is missing: " + path);
        }
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new IOException("Language file is not a regular file: " + path);
        }
    }

    private Optional<String> locale(Path path) {
        if (Files.isSymbolicLink(path)
                || (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                && !Files.notExists(path, LinkOption.NOFOLLOW_LINKS))) {
            return Optional.empty();
        }
        String fileName = path.getFileName().toString();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".toml")) {
            return Optional.empty();
        }
        String locale = fileName.substring(0, fileName.length() - ".toml".length());
        return LOCALE_PATTERN.matcher(locale).matches() ? Optional.of(locale) : Optional.empty();
    }

    private String requireLocale(String locale) {
        String normalized = locale == null ? "" : locale.trim();
        if (!LOCALE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Language locale must be a safe name without a path or extension");
        }
        return normalized;
    }

    private String canonicalLocale(String locale) {
        String requiredLocale = requireLocale(locale);
        if (CATALOG.englishLocale().equalsIgnoreCase(requiredLocale)) {
            return CATALOG.englishLocale();
        }
        if (remoteCatalog != null) {
            for (String available : remoteCatalog.availableLocales()) {
                if (available.equalsIgnoreCase(requiredLocale)) {
                    return available;
                }
            }
        }
        return requiredLocale;
    }

    private static boolean sameLocale(String first, String second) {
        return first.replace('-', '_').equalsIgnoreCase(second.replace('-', '_'));
    }

    private boolean isRepositoryLocale(String locale) {
        for (String available : VolmitLocales.nonEnglish()) {
            if (available.equalsIgnoreCase(locale)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> englishHeader(String locale) {
        return LanguageFileHeader.render(new LanguageFileHeader.Options(
                "Rift", locale,
                List.of("runtime.prefix stores the styled plugin name used by {prefix}.",
                        "Messages supply the separator and spacing. Remove {prefix}&r &7› &7 to hide the complete chat prefix."),
                List.of("Colors and styles: &0-&f, &k-&r.", "RGB colors: &#RRGGBB, &xRRGGBB, &x&R&R&G&G&B&B, [RRGGBB].", "MiniMessage supports custom formatting. Put a backslash before & or [ to display it literally."),
                Map.ofEntries(
                        Map.entry("after", "new value"),
                        Map.entry("argument", "argument"),
                        Map.entry("before", "previous value"),
                        Map.entry("category", "category"),
                        Map.entry("command", "command path"),
                        Map.entry("count", "entry count"),
                        Map.entry("detail", "entry detail"),
                        Map.entry("group", "message group"),
                        Map.entry("id", "quarantine ID"),
                        Map.entry("key", "parameter key"),
                        Map.entry("label", "display label"),
                        Map.entry("line", "line number"),
                        Map.entry("locale", "locale"),
                        Map.entry("maximum", "maximum input length"),
                        Map.entry("name", "entry name"),
                        Map.entry("operation", "operation"),
                        Map.entry("parameter", "parameter"),
                        Map.entry("permission", "permission"),
                        Map.entry("personal", "a player's personal locale when it differs from the server default"),
                        Map.entry("player", "player"),
                        Map.entry("path", "local report path"),
                        Map.entry("prefix", "the styled name from runtime.prefix; separators and spacing belong to each message"),
                        Map.entry("reason", "failure detail"),
                        Map.entry("seconds", "duration"),
                        Map.entry("section", "editor section"),
                        Map.entry("setting", "setting"),
                        Map.entry("target", "selection target"),
                        Map.entry("title", "section title"),
                        Map.entry("type", "value type"),
                        Map.entry("url", "uploaded report URL"),
                        Map.entry("value", "current or new value"),
                        Map.entry("variables", "allowed placeholders"),
                        Map.entry("version", "plugin version"),
                        Map.entry("world", "world")
                )));
    }

    private void validateCatalogTemplates() {
        for (MessageKey key : CATALOG.keys()) {
            MessageValue value = key.englishValue();
            if (value instanceof TextValue text) {
                validateTemplate("catalog:" + key.id(), text.template(), sampleArguments(value.placeholders()));
            }
        }
    }

    private LocalizationSnapshot selectedSnapshot(CommandSender sender) {
        PluginLanguageService activeSelections = selections;
        if (activeSelections == null) {
            return manager.snapshot();
        }
        return sender instanceof Player player
                ? activeSelections.snapshot(player.getUniqueId())
                : activeSelections.snapshot();
    }

    private ComponentText render(LocalizationSnapshot snapshot, TextKey key, MessageArgs arguments, boolean withoutChatPrefix) {
        TextKey definition = (TextKey) CATALOG.require(key.id());
        MessageArgs resolvedArguments = argumentsWithPrefix(definition, arguments, "<rift_prefix>");
        String template = snapshot.resolve(definition, resolvedArguments).template();
        if (withoutChatPrefix && template.startsWith(RiftMessages.CHAT_PREFIX)) {
            template = template.substring(RiftMessages.CHAT_PREFIX.length());
        }
        return renderTemplate(template, resolvedArguments, prefixComponent(snapshot, definition, arguments));
    }

    private Component prefixComponent(LocalizationSnapshot snapshot, MessageKey definition, MessageArgs arguments) {
        if (arguments != null && arguments.names().contains("plugin")) {
            String name = String.valueOf(arguments.require("plugin").value());
            if (!name.equalsIgnoreCase("Rift")) {
                return Component.text(ColorFormatter.stripColor(name));
            }
        }
        Component prefix = MINI_MESSAGE.deserialize(ComponentText.normalizeMarkup(renderPrefix(snapshot)));
        return definition.id().equals(RiftMessages.VERSION.id())
                ? Component.text(ComponentText.component(prefix).plain())
                : prefix;
    }

    private ComponentText renderTemplate(String template, MessageArgs arguments, Component prefix) {
        MessageArgs.Builder replacements = MessageArgs.builder();
        for (MessageArgument argument : arguments.arguments().values()) {
            String value = String.valueOf(argument.value());
            replacements.trusted(argument.name(), argument.kind() == MessageArgumentKind.UNTRUSTED
                    ? ColorFormatter.stripColor(value).replace("\\", "\\\\").replace("<", "\\<")
                    : ComponentText.normalizeMarkup(value));
        }
        String rendered = interpolate(ComponentText.normalizeMarkup(template), replacements.build());
        return ComponentText.component(MINI_MESSAGE.deserialize(rendered,
                Placeholder.component("rift_prefix", prefix)));
    }

    private String renderPrefix(LocalizationSnapshot snapshot) {
        String template = snapshot.resolve(RiftMessages.PREFIX, MessageArgs.empty()).template();
        return interpolate(template, MessageArgs.empty());
    }

    private MessageArgs argumentsWithPrefix(TextKey key, MessageArgs arguments, String prefix) {
        MessageArgs resolved = arguments == null ? MessageArgs.empty() : arguments;
        if (!key.placeholders().contains("prefix")) {
            return resolved;
        }
        if (resolved.names().contains("prefix")) {
            throw new IllegalArgumentException("The prefix message argument is managed by Rift");
        }
        MessageArgs.Builder builder = MessageArgs.builder();
        for (MessageArgument argument : resolved.arguments().values()) {
            if (key.id().equals("language.selection.preparing") && argument.name().equals("target")
                    && "Rift".equalsIgnoreCase(String.valueOf(argument.value()))) {
                builder.trusted("target", prefix);
            } else if (!argument.name().equals("plugin") || key.placeholders().contains("plugin")) {
                builder.add(argument);
            }
        }
        builder.trusted("prefix", prefix);
        return builder.build();
    }

    private void validateTemplate(String path, String template, MessageArgs arguments) {
        validatePlaceholderPlacement(path, template);
        try {
            STRICT_MINI_MESSAGE.deserialize(interpolate(template, arguments));
            String normalized = ComponentText.normalizeMarkup(template);
            MINI_MESSAGE.deserialize(interpolate(normalized, arguments));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(path + ": invalid message markup", exception);
        }
    }

    private void validatePlaceholderPlacement(String path, String template) {
        boolean insideTag = false;
        for (int index = 0; index < template.length(); index++) {
            char current = template.charAt(index);
            if (current == '\\') {
                index++;
                continue;
            }
            if (current == '<') {
                insideTag = true;
                continue;
            }
            if (current == '>') {
                insideTag = false;
                continue;
            }
            if (insideTag && current == '{'
                    && (index + 1 >= template.length() || template.charAt(index + 1) != '{')) {
                throw new IllegalArgumentException(path + ": message placeholders cannot be used inside MiniMessage tags");
            }
        }
    }

    private MessageArgs sampleArguments(Set<String> placeholders) {
        MessageArgs.Builder builder = MessageArgs.builder();
        for (String placeholder : placeholders) {
            builder.untrusted(placeholder, "value");
        }
        return builder.build();
    }

    private String interpolate(String template, MessageArgs arguments) {
        StringBuilder output = new StringBuilder(template.length());
        int index = 0;
        while (index < template.length()) {
            char current = template.charAt(index);
            if (current == '{' && index + 1 < template.length() && template.charAt(index + 1) == '{') {
                output.append('{');
                index += 2;
                continue;
            }
            if (current == '}' && index + 1 < template.length() && template.charAt(index + 1) == '}') {
                output.append('}');
                index += 2;
                continue;
            }
            if (current != '{') {
                output.append(current);
                index++;
                continue;
            }
            int end = template.indexOf('}', index + 1);
            if (end < 0) {
                throw new IllegalArgumentException("Unclosed message placeholder");
            }
            String name = template.substring(index + 1, end);
            MessageArgument argument = arguments.require(name);
            String replacement = String.valueOf(argument.value());
            if (argument.kind() == MessageArgumentKind.UNTRUSTED) {
                replacement = escapeUntrusted(replacement);
            }
            output.append(replacement);
            index = end + 1;
        }
        return output.toString();
    }

    private String previewValue(LocalizationSnapshot snapshot, MessageKey definition, String template) {
        MessageArgs.Builder arguments = MessageArgs.builder();
        for (String placeholder : definition.placeholders()) {
            if (placeholder.equals("prefix")) {
                MessageValue prefixValue = snapshot.value(RiftMessages.PREFIX);
                if (!(prefixValue instanceof TextValue prefixText)) {
                    throw new IllegalStateException("Language prefix is not text");
                }
                arguments.trusted(placeholder, "<rift_prefix>");
            } else {
                arguments.untrusted(placeholder, "[" + placeholder + "]");
            }
        }
        return renderTemplate(template, arguments.build(), prefixComponent(snapshot, definition, MessageArgs.empty())).miniMessage();
    }

    private String escapeUntrusted(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '&' || current == '[') {
                escaped.append('\\');
            }
            escaped.append(current);
        }
        return MINI_MESSAGE.escapeTags(escaped.toString());
    }

    public record EditableMessage(
            String id,
            String effectiveValue,
            String previewValue,
            Set<String> placeholders
    ) {
        public EditableMessage {
            placeholders = Set.copyOf(placeholders);
        }
    }

    private record FileSource(boolean existed, byte[] bytes, String content) {
        private FileSource {
            bytes = Arrays.copyOf(bytes, bytes.length);
        }
    }

    public record PreparedLanguage(
            String locale,
            File file,
            LocalizationSnapshot snapshot,
            boolean selectionReady
    ) {
    }
}
