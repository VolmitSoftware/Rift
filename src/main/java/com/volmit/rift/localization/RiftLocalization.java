package com.volmit.rift.localization;

import art.arcane.volmlib.util.director.DirectorTextResolver;
import art.arcane.volmlib.util.format.ColorFormatter;
import art.arcane.volmlib.util.io.AtomicFileIO;
import art.arcane.volmlib.util.localization.BukkitLanguageSwitcher;
import art.arcane.volmlib.util.localization.GitHubLanguageSource;
import art.arcane.volmlib.util.localization.LanguageCatalogMerge;
import art.arcane.volmlib.util.localization.LanguageDirectory;
import art.arcane.volmlib.util.localization.LanguageFileHeader;
import art.arcane.volmlib.util.localization.LanguageMeta;
import art.arcane.volmlib.util.localization.LanguageReferenceRenderer;
import art.arcane.volmlib.util.localization.LanguageSourceManifest;
import art.arcane.volmlib.util.localization.LanguageUpstreamStore;
import art.arcane.volmlib.util.localization.LanguageUpstreamSync;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.logging.Level;

public final class RiftLocalization implements AutoCloseable {
    public static final String MANIFEST_RESOURCE = "rift-language-source.properties";
    private static final long MAXIMUM_LANGUAGE_BYTES = LanguageDirectory.MAXIMUM_BYTES;
    private static final int MAXIMUM_BASE_DEPTH = 4;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final MiniMessage STRICT_MINI_MESSAGE = MiniMessage.builder().strict(true).build();
    private static final PluralSelector ENGLISH_PLURALS = PluralSelector.oneOther();
    private static final MessageCatalog CATALOG = RiftMessages.catalog();

    private final Plugin plugin;
    private final Supplier<RiftConfig> config;
    private final File directory;
    private final LanguageDirectory languageDirectory;
    private final Path preferenceFile;
    private final LocalizationManager manager;
    private final AtomicReference<File> activeFile;
    private final AtomicReference<String> activeLocale;
    private final LanguageSourceManifest manifest;
    private final LanguageUpstreamSync sync;
    private final Throwable sourceFailure;
    private final Map<String, MetaStamp> metaStamps = new ConcurrentHashMap<>();
    private volatile List<String> locales = List.of();
    private volatile Map<String, LanguageMeta> metas = Map.of();
    private volatile BiConsumer<File, String> selfWriteListener;
    private volatile PluginLanguageService selections;

    public RiftLocalization(Plugin plugin, Supplier<RiftConfig> config, File directory) {
        this(plugin, config, directory, GitHubLanguageSource.API_ROOT, GitHubLanguageSource.RAW_ROOT,
                LanguageUpstreamSync.MINIMUM_CHECK_INTERVAL);
    }

    RiftLocalization(
            Plugin plugin,
            Supplier<RiftConfig> config,
            File directory,
            URI apiRoot,
            URI rawRoot,
            Duration minimumCheckInterval
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.config = Objects.requireNonNull(config, "config");
        this.directory = Objects.requireNonNull(directory, "directory");
        languageDirectory = new LanguageDirectory(directory);
        preferenceFile = directory.toPath().resolve("language-preferences.properties");
        validateCatalogTemplates();
        manager = new LocalizationManager(LocalizationCandidate.english(CATALOG, ENGLISH_PLURALS));
        activeFile = new AtomicReference<>(file(VolmitLocales.ENGLISH));
        activeLocale = new AtomicReference<>(VolmitLocales.ENGLISH);
        LanguageSourceManifest loadedManifest = null;
        LanguageUpstreamSync loadedSync = null;
        Throwable failure = null;
        try {
            loadedManifest = LanguageSourceManifest.load(RiftLocalization.class.getClassLoader(), MANIFEST_RESOURCE);
            GitHubLanguageSource source = new GitHubLanguageSource(loadedManifest, "Rift", apiRoot, rawRoot);
            LanguageUpstreamStore store = new LanguageUpstreamStore(languageDirectory, loadedManifest);
            loadedSync = new LanguageUpstreamSync(new LanguageUpstreamSync.Options(
                    "Rift",
                    CATALOG,
                    languageDirectory,
                    loadedManifest,
                    source,
                    store,
                    RiftLocalization::referenceToml,
                    this::validateContent,
                    this::commitUpstream,
                    this::noteUpstreamWrite,
                    this::applyUpstream,
                    plugin.getLogger()
            ), minimumCheckInterval);
        } catch (RuntimeException exception) {
            failure = exception;
        }
        manifest = loadedManifest;
        sync = loadedSync;
        sourceFailure = failure;
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
        ensureEnglish();
        File target = file(locale);
        requireRegularLanguageFile(target, true);
        if (!target.exists() && !isRepositoryLocale(locale)) {
            createCustomLanguageIfMissing(locale);
        }
        ArrayList<LocaleOverlay> overlays = new ArrayList<>();
        if (target.isFile()) {
            try {
                FileSource source = readFileSource(target);
                overlays.add(createOverlay(locale, target.getPath(), parseEditableLanguage(target, source.content())));
                overlays.addAll(baseOverlays(locale, source.content()));
            } catch (IOException | RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Using English for unreadable language file " + target, exception);
            }
        }
        PreparedLanguage prepared = createPrepared(locale, target, overlays, target.isFile());
        refreshAvailableLocales();
        return prepared;
    }

    public PreparedLanguage prepareSnapshot(String requestedLocale, String raw) throws IOException {
        if (raw == null) {
            throw new IOException("Language snapshot is missing");
        }
        String locale = canonicalLocale(requestedLocale);
        File target = file(locale);
        requireRegularLanguageFile(target, false);
        Map<String, String> values = parseStrictValues(raw, locale);
        ArrayList<LocaleOverlay> overlays = new ArrayList<>();
        overlays.add(createOverlay(locale, target.getPath(), values));
        overlays.addAll(baseOverlays(locale, raw));
        return createPrepared(locale, target, overlays, true);
    }

    public synchronized PreparedLanguage englishFallback(String locale) throws IOException {
        String requiredLocale = canonicalLocale(locale);
        prepareLanguageDirectory();
        ensureEnglish();
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
        ArrayList<LocaleOverlay> overlays = new ArrayList<>();
        overlays.add(createOverlay(requiredLocale, target.getPath(), values));
        overlays.addAll(baseOverlays(requiredLocale, content));
        PreparedLanguage prepared = createPrepared(requiredLocale, target, overlays, true);
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
        try {
            List<String> installed = languageDirectory.scan();
            ArrayList<File> files = new ArrayList<>(installed.size());
            for (String locale : installed) {
                files.add(languageDirectory.languageFile(locale).toFile());
            }
            return List.copyOf(files);
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

    public void refreshAvailableLocales() {
        LinkedHashSet<String> choices = new LinkedHashSet<>();
        choices.add(CATALOG.englishLocale());
        if (manifest != null) {
            choices.addAll(manifest.locales());
        }
        List<String> installed = List.of();
        try {
            installed = languageDirectory.scan();
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Unable to enumerate Rift languages in " + directory, exception);
        }
        choices.addAll(installed);
        LinkedHashMap<String, LanguageMeta> discoveredMetas = new LinkedHashMap<>();
        metaStamps.keySet().retainAll(installed);
        for (String locale : installed) {
            LanguageMeta meta = cachedMeta(locale);
            if (!meta.isEmpty()) {
                discoveredMetas.put(locale, meta);
            }
        }
        ArrayList<String> discovered = new ArrayList<>(choices);
        discovered.sort(String.CASE_INSENSITIVE_ORDER);
        metas = Map.copyOf(discoveredMetas);
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
        LanguageMeta meta = metas.getOrDefault(normalized, LanguageMeta.empty());
        return meta.name().or(() -> VolmitLocales.displayName(normalized)).orElse(normalized);
    }

    public LanguageMeta meta(String locale) {
        return metas.getOrDefault(locale == null ? "" : locale.trim(), LanguageMeta.empty());
    }

    public BukkitLanguageSwitcher.LanguageUpdateAction updateAction() {
        return this::startUpstreamCheck;
    }

    public BukkitLanguageSwitcher.LocaleDisplayNames displayNames() {
        return locale -> metas.getOrDefault(locale, LanguageMeta.empty()).name().orElse(null);
    }

    public CompletableFuture<LanguageUpstreamSync.SyncReport> startUpstreamCheck() {
        if (sync == null) {
            return CompletableFuture.completedFuture(LanguageUpstreamSync.SyncReport.failed(
                    sourceDescription(), System.currentTimeMillis()));
        }
        return sync.checkAsync().thenApply(this::logUpstreamReport);
    }

    public CompletableFuture<String> requestInstall(String locale) {
        String requiredLocale = canonicalLocale(locale);
        if (file(requiredLocale).isFile()) {
            try {
                return CompletableFuture.completedFuture(languageDirectory.read(requiredLocale));
            } catch (IOException exception) {
                return CompletableFuture.failedFuture(exception);
            }
        }
        if (sync == null || !isRepositoryLocale(requiredLocale)) {
            return CompletableFuture.failedFuture(new IOException("Language " + requiredLocale
                    + " is not available from " + sourceDescription()));
        }
        plugin.getLogger().info("Installing Rift language " + requiredLocale + " from " + sourceDescription() + "...");
        return sync.installAsync(requiredLocale).thenApply(content -> {
            plugin.getLogger().info("Installed Rift language " + requiredLocale + " to "
                    + file(requiredLocale).toPath().toAbsolutePath().normalize() + ".");
            return content;
        });
    }

    public Optional<Throwable> sourceFailure() {
        return Optional.ofNullable(sourceFailure);
    }

    public String sourceDescription() {
        if (manifest == null) {
            return "unavailable";
        }
        return manifest.repository() + "@" + manifest.branch();
    }

    public Optional<LanguageUpstreamSync.Status> upstreamStatus() {
        return sync == null ? Optional.empty() : Optional.of(sync.status());
    }

    public boolean isRepositoryLocale(String locale) {
        return manifest != null && manifest.contains(locale);
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
        if (sync != null) {
            sync.close();
        }
    }

    static String referenceToml() {
        return LanguageReferenceRenderer.render(CATALOG, englishHeader(CATALOG.englishLocale()));
    }

    void validateContent(String locale, String content) throws IOException {
        Map<String, String> values = parseStrictValues(content, locale);
        LocaleOverlay overlay = createOverlay(locale, "upstream:" + locale, values);
        try {
            LocalizationSnapshot.create(new LocalizationCandidate(CATALOG, List.of(overlay), ENGLISH_PLURALS));
        } catch (RuntimeException exception) {
            throw new IOException("Language content is invalid: " + locale, exception);
        }
    }

    private <T> T commitUpstream(LanguageUpstreamSync.CommitAction<T> action) throws IOException {
        PluginLanguageService activeSelections = selections;
        if (activeSelections == null) {
            return action.apply();
        }
        return activeSelections.commitUpdate(action::apply);
    }

    private void noteUpstreamWrite(Path file, String content) {
        noteSelfWrite(file.toFile(), content);
    }

    private void applyUpstream(String locale, String content, LanguageCatalogMerge.MergeResult result)
            throws IOException {
        PreparedLanguage prepared = prepareSnapshot(locale, content);
        refreshAvailableLocales();
        PluginLanguageService activeSelections = selections;
        if (activeSelections != null) {
            activeSelections.cache(prepared.locale(), selectionSnapshot(prepared.locale(), prepared.snapshot()));
        }
        if (sameLocale(prepared.locale(), activeLocale.get())) {
            manager.install(prepared.snapshot());
            activeFile.set(prepared.file());
        }
    }

    private LanguageUpstreamSync.SyncReport logUpstreamReport(LanguageUpstreamSync.SyncReport report) {
        String revision = report.revision().map(sha -> sha.substring(0, 7)).orElse("unknown");
        for (String locale : report.changedLocales()) {
            LanguageCatalogMerge.MergeResult result = report.merged().get(locale);
            String source = locale.equals(CATALOG.englishLocale())
                    ? "the built-in catalog"
                    : sourceDescription() + " (" + revision + ")";
            plugin.getLogger().info("Updated Rift language " + locale + " from " + source + ": " + result.summary() + ".");
        }
        if (report.coolingDown()) {
            plugin.getLogger().info("Rift language source " + sourceDescription()
                    + " was checked recently; the next check is allowed in " + report.cooldownRemaining() + ".");
        } else if (report.failure().isEmpty() && report.changedLocales().isEmpty() && report.failures().isEmpty()
                && report.revision().isPresent()) {
            plugin.getLogger().info("Rift languages match " + sourceDescription() + " (" + revision + ").");
        }
        return report;
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
        ensureEnglish();
        File target = file(requiredLocale);
        requireRegularLanguageFile(target, true);
        if (!target.exists() && isRepositoryLocale(requiredLocale)) {
            if (sync == null) {
                throw new IOException("Language " + requiredLocale + " is not installed and the language source is "
                        + sourceDescription());
            }
            plugin.getLogger().info("Installing Rift language " + requiredLocale + " from " + sourceDescription() + "...");
            sync.install(requiredLocale);
            plugin.getLogger().info("Installed Rift language " + requiredLocale + " to "
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

    private List<LocaleOverlay> baseOverlays(String locale, String content) throws IOException {
        ArrayList<LocaleOverlay> overlays = new ArrayList<>();
        HashSet<String> visited = new HashSet<>();
        visited.add(normalizedLocale(locale));
        LanguageMeta meta = metaFromContent(locale, content);
        int depth = 0;
        while (meta.base().isPresent() && depth < MAXIMUM_BASE_DEPTH) {
            String base = canonicalLocale(meta.base().get());
            if (!visited.add(normalizedLocale(base)) || sameLocale(base, CATALOG.englishLocale())) {
                break;
            }
            File baseFile = file(base);
            if (!baseFile.isFile()) {
                break;
            }
            String baseContent = LanguageDirectory.readContent(baseFile.toPath());
            overlays.add(createOverlay(base, baseFile.getPath(), TomlLanguageParser.parseValidText(baseContent, CATALOG)));
            meta = metaFromContent(base, baseContent);
            depth++;
        }
        return overlays;
    }

    private LanguageMeta metaFromContent(String locale, String content) {
        try {
            return LanguageMeta.parse(content).withoutSelfBase(locale);
        } catch (IOException exception) {
            return LanguageMeta.empty();
        }
    }

    private LanguageMeta cachedMeta(String locale) {
        Path path = languageDirectory.languageFile(locale);
        try {
            long modified = Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis();
            long size = Files.size(path);
            MetaStamp cached = metaStamps.get(locale);
            if (cached != null && cached.modified() == modified && cached.size() == size) {
                return cached.meta();
            }
            LanguageMeta meta = languageDirectory.meta(locale);
            metaStamps.put(locale, new MetaStamp(modified, size, meta));
            return meta;
        } catch (IOException exception) {
            metaStamps.remove(locale);
            plugin.getLogger().log(Level.FINE, "Unable to read Rift language metadata for " + locale, exception);
            return LanguageMeta.empty();
        }
    }

    private Map<String, String> parseEditableLanguage(File file, String content) throws IOException {
        try {
            return TomlLanguageParser.parseValidText(content, CATALOG);
        } catch (IOException exception) {
            throw new IOException("Invalid TOML in " + file.getName() + ": " + exception.getMessage(), exception);
        }
    }

    private Map<String, String> parseStrictValues(String content, String locale) throws IOException {
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

    private void ensureEnglish() throws IOException {
        File english = file(CATALOG.englishLocale());
        requireRegularLanguageFile(english, true);
        if (sync != null) {
            try {
                sync.mergeEnglish().ifPresent(result -> plugin.getLogger().info("Updated Rift language "
                        + CATALOG.englishLocale() + " from the built-in catalog: " + result.summary() + "."));
                return;
            } catch (IOException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IOException("Unable to refresh the Rift English language file", exception);
            }
        }
        if (english.exists()) {
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
        String content = LanguageReferenceRenderer.render(CATALOG, englishHeader(locale),
                LanguageMeta.of(locale, CATALOG.englishLocale()));
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
        return LanguageDirectory.localeFromName(path.getFileName().toString());
    }

    private String requireLocale(String locale) {
        return LanguageDirectory.requireLocale(locale);
    }

    private String canonicalLocale(String locale) {
        String requiredLocale = requireLocale(locale);
        if (CATALOG.englishLocale().equalsIgnoreCase(requiredLocale)) {
            return CATALOG.englishLocale();
        }
        if (manifest != null && manifest.contains(requiredLocale)) {
            return manifest.canonical(requiredLocale);
        }
        return requiredLocale;
    }

    private static boolean sameLocale(String first, String second) {
        return LanguageDirectory.sameLocale(first, second);
    }

    private static String normalizedLocale(String locale) {
        return locale.replace('-', '_').toLowerCase(Locale.ROOT);
    }

    private static List<String> englishHeader(String locale) {
        return LanguageFileHeader.render(new LanguageFileHeader.Options(
                "Rift", locale,
                List.of("runtime.prefix stores the styled plugin name used by {prefix}.",
                        "Messages supply the separator and spacing. Remove {prefix}&r &7› &7 to hide the complete chat prefix."),
                List.of("Colors and styles: &0-&f, &k-&r.", "RGB colors: &#RRGGBB, &xRRGGBB, &x&R&R&G&G&B&B, [RRGGBB].", "MiniMessage supports custom formatting. Put a backslash before & or [ to display it literally."),
                Map.ofEntries(
                        Map.entry("added", "messages added by a language update"),
                        Map.entry("after", "new value"),
                        Map.entry("argument", "argument"),
                        Map.entry("before", "previous value"),
                        Map.entry("category", "category"),
                        Map.entry("command", "command path"),
                        Map.entry("count", "entry count"),
                        Map.entry("detail", "entry detail"),
                        Map.entry("group", "message group"),
                        Map.entry("id", "quarantine ID"),
                        Map.entry("kept", "local edits kept by a language update"),
                        Map.entry("key", "parameter key"),
                        Map.entry("label", "display label"),
                        Map.entry("line", "line number"),
                        Map.entry("limit", "maximum players admitted to a world"),
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
                        Map.entry("remaining", "time until the next language check is allowed"),
                        Map.entry("seconds", "duration"),
                        Map.entry("section", "editor section"),
                        Map.entry("setting", "setting"),
                        Map.entry("step", "numeric change per click"),
                        Map.entry("target", "selection target"),
                        Map.entry("title", "section title"),
                        Map.entry("type", "value type"),
                        Map.entry("updated", "messages changed by a language update"),
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

    private record MetaStamp(long modified, long size, LanguageMeta meta) {
    }
}
