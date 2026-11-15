package io.github.thebusybiscuit.slimefun4.implementation;

import city.norain.slimefun4.ServerVersion;
import city.norain.slimefun4.SlimefunExtended;
import city.norain.slimefun4.timings.SQLProfiler;
import com.xzavier0722.mc.plugin.slimefun4.chat.PlayerChatCatcher;
import com.xzavier0722.mc.plugin.slimefun4.storage.migrator.BlockStorageMigrator;
import com.xzavier0722.mc.plugin.slimefun4.storage.migrator.PlayerProfileMigrator;
import com.xzavier0722.mc.plugin.slimefuncomplib.ICompatibleSlimefun;
import io.github.bakedlibs.dough.config.Config;
import io.github.bakedlibs.dough.protection.ProtectionManager;
import io.github.thebusybiscuit.slimefun4.api.MinecraftVersion;
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.api.exceptions.TagMisconfigurationException;
import io.github.thebusybiscuit.slimefun4.api.geo.GEOResource;
import io.github.thebusybiscuit.slimefun4.api.gps.GPSNetwork;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.core.SlimefunRegistry;
import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.config.SlimefunConfigManager;
import io.github.thebusybiscuit.slimefun4.core.config.SlimefunDatabaseManager;
import io.github.thebusybiscuit.slimefun4.core.networks.NetworkManager;
import io.github.thebusybiscuit.slimefun4.core.services.AnalyticsService;
import io.github.thebusybiscuit.slimefun4.core.services.AutoSavingService;
import io.github.thebusybiscuit.slimefun4.core.services.BackupService;
import io.github.thebusybiscuit.slimefun4.core.services.BlockDataService;
import io.github.thebusybiscuit.slimefun4.core.services.CustomItemDataService;
import io.github.thebusybiscuit.slimefun4.core.services.CustomTextureService;
import io.github.thebusybiscuit.slimefun4.core.services.ItemStackService;
import io.github.thebusybiscuit.slimefun4.core.services.LocalizationService;
import io.github.thebusybiscuit.slimefun4.core.services.MetricsService;
import io.github.thebusybiscuit.slimefun4.core.services.MinecraftRecipeService;
import io.github.thebusybiscuit.slimefun4.core.services.PerWorldSettingsService;
import io.github.thebusybiscuit.slimefun4.core.services.PermissionsService;
import io.github.thebusybiscuit.slimefun4.core.services.ThreadService;
import io.github.thebusybiscuit.slimefun4.core.services.UpdaterService;
import io.github.thebusybiscuit.slimefun4.core.services.github.GitHubService;
import io.github.thebusybiscuit.slimefun4.core.services.holograms.HologramsService;
import io.github.thebusybiscuit.slimefun4.core.services.profiler.SlimefunProfiler;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundService;
import io.github.thebusybiscuit.slimefun4.implementation.items.altar.AncientAltar;
import io.github.thebusybiscuit.slimefun4.implementation.items.altar.AncientPedestal;
import io.github.thebusybiscuit.slimefun4.implementation.items.backpacks.Cooler;
import io.github.thebusybiscuit.slimefun4.implementation.items.magical.BeeWings;
import io.github.thebusybiscuit.slimefun4.implementation.items.tools.GrapplingHook;
import io.github.thebusybiscuit.slimefun4.implementation.items.weapons.SeismicAxe;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.AncientAltarListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.AutoCrafterListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.BackpackListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.BeeWingsListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.BlockListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.BlockPhysicsListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.ButcherAndroidListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.CargoNodeListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.CoolerListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.DeathpointListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.DebugFishListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.DispenserListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.ElytraImpactListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.EnhancedFurnaceListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.ExplosionsListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.GadgetsListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.GrapplingHookListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.HopperListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.ItemDropListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.ItemPickupListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.JoinListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.MiddleClickListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.MiningAndroidListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.MultiBlockListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.NetworkListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.PlayerProfileListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.RadioactivityListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.SeismicAxeListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.SlimefunBootsListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.SlimefunBowListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.SlimefunGuideListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.SlimefunGuideRecipeListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.SlimefunItemConsumeListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.SlimefunItemHitListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.SlimefunItemInteractListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.SoulboundListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.TalismanListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.VersionedMiddleClickListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.VillagerTradingListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.crafting.AnvilListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.crafting.BrewingStandListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.crafting.CartographyTableListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.crafting.CauldronListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.crafting.CraftingTableListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.crafting.GrindstoneListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.crafting.SmithingTableListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.crafting.VanillaCrafterListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.entity.BeeListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.entity.EntityInteractionListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.entity.FireworksListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.entity.IronGolemListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.entity.MobDropListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.entity.PiglinListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.entity.WitherListener;
import io.github.thebusybiscuit.slimefun4.implementation.resources.GEOResourcesSetup;
import io.github.thebusybiscuit.slimefun4.implementation.setup.PostSetup;
import io.github.thebusybiscuit.slimefun4.implementation.setup.ResearchSetup;
import io.github.thebusybiscuit.slimefun4.implementation.setup.SlimefunItemSetup;
import io.github.thebusybiscuit.slimefun4.implementation.tasks.SlimefunStartupTask;
import io.github.thebusybiscuit.slimefun4.implementation.tasks.TickerTask;
import io.github.thebusybiscuit.slimefun4.implementation.tasks.armor.RadiationTask;
import io.github.thebusybiscuit.slimefun4.implementation.tasks.armor.RainbowArmorTask;
import io.github.thebusybiscuit.slimefun4.implementation.tasks.armor.SlimefunArmorTask;
import io.github.thebusybiscuit.slimefun4.implementation.tasks.armor.SolarHelmetTask;
import io.github.thebusybiscuit.slimefun4.integrations.IntegrationsManager;
import io.github.thebusybiscuit.slimefun4.utils.NumberUtils;
import io.github.thebusybiscuit.slimefun4.utils.tags.SlimefunTag;
import io.papermc.lib.PaperLib;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.MenuListener;
import net.guizhanss.slimefun4.updater.AutoUpdateTask;
import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginLoader;

/**
 * This is the main class of Slimefun.
 * This is where all the magic starts, take a look around.
 *
 * @author TheBusyBiscuit
 */
public final class Slimefun extends JavaPlugin implements SlimefunAddon, ICompatibleSlimefun {

    /**
     * This is the Java version we recommend server owners to use.
     * This does not necessarily mean that it's the minimum version
     * required to run Slimefun.
     */
    private static final int RECOMMENDED_JAVA_VERSION = 17;

    /**
     * Our static instance of {@link Slimefun}.
     * Make sure to clean this up in {@link #onDisable()}!
     */
    private static Slimefun instance;

    /**
     * Keep track of which {@link MinecraftVersion} we are on.
     */
    private MinecraftVersion minecraftVersion = MinecraftVersion.UNKNOWN;

    /**
     * Keep track of whether this is a fresh install or a regular boot up.
     */
    private boolean isNewlyInstalled = false;

    // Various things we need
    private final SlimefunConfigManager cfgManager = new SlimefunConfigManager(this);
    private final SlimefunDatabaseManager databaseManager = new SlimefunDatabaseManager(this);
    private final SlimefunRegistry registry = new SlimefunRegistry();
    private final SlimefunCommand command = new SlimefunCommand(this);
    private final TickerTask ticker = new TickerTask();
    private PlayerChatCatcher chatCatcher;

    // Services - Systems that fulfill certain tasks, treat them as a black box
    private final CustomItemDataService itemDataService = new CustomItemDataService(this, "slimefun_item");
    private final BlockDataService blockDataService = new BlockDataService(this, "slimefun_block");
    private final CustomTextureService textureService = new CustomTextureService(new Config(this, "item-models.yml"));
    private final GitHubService gitHubService = new GitHubService("LiuyeStudio/Slimefun4-Folia");
    private final UpdaterService updaterService =
            new UpdaterService(this, getDescription().getVersion(), getFile());
    private final MetricsService metricsService = new MetricsService(this);
    private final AutoSavingService autoSavingService = new AutoSavingService();
    private final BackupService backupService = new BackupService();
    private final PermissionsService permissionsService = new PermissionsService(this);
    private final PerWorldSettingsService worldSettingsService = new PerWorldSettingsService(this);
    private final MinecraftRecipeService recipeService = new MinecraftRecipeService(this);
    private final HologramsService hologramsService = new HologramsService(this);
    private final SoundService soundService = new SoundService(this);
    private final ThreadService threadService = new ThreadService(this);
    private final AnalyticsService analyticsService = new AnalyticsService(this);
    private final ItemStackService itemStackService = new ItemStackService();

    // Some other things we need
    private final IntegrationsManager integrations = new IntegrationsManager(this);
    private final SlimefunProfiler profiler = new SlimefunProfiler();
    private final SQLProfiler sqlProfiler = new SQLProfiler();
    private final GPSNetwork gpsNetwork = new GPSNetwork(this);

    // Even more things we need
    private NetworkManager networkManager;
    private LocalizationService local;

    // Important config files for Slimefun
    private final Config items = new Config(this, "Items.yml");
    private final Config researches = new Config(this, "Researches.yml");

    // Listeners that need to be accessed elsewhere
    private final GrapplingHookListener grapplingHookListener = new GrapplingHookListener();
    private final BackpackListener backpackListener = new BackpackListener();
    private final SlimefunBowListener bowListener = new SlimefunBowListener();

    /**
     * Our default constructor for {@link Slimefun}.
     */
    public Slimefun() {
        super();
    }

    /**
     * This constructor is invoked in Unit Test environments only.
     *
     * @param loader
     *            Our {@link JavaPluginLoader}
     * @param description
     *            A {@link PluginDescriptionFile}
     * @param dataFolder
     *            The data folder
     * @param file
     *            A {@link File} for this {@link Plugin}
     */
    @ParametersAreNonnullByDefault
    public Slimefun(JavaPluginLoader loader, PluginDescriptionFile description, File dataFolder, File file) {
        super(loader, description, dataFolder, file);

        // This is only invoked during a Unit Test
        minecraftVersion = MinecraftVersion.UNIT_TEST;
    }

    /**
     * This is called when the {@link Plugin} has been loaded and enabled on a {@link Server}.
     */
    @Override
    public void onEnable() {
        setInstance(this);

        if (isUnitTest()) {
            // We handle Unit Tests seperately.
            onUnitTestStart();
        } else if (isVersionUnsupported()) {
            // We wanna ensure that the Server uses a compatible version of Minecraft.
            getServer().getPluginManager().disablePlugin(this);
        } else if (!SlimefunExtended.checkEnvironment(this)) {
            // We want to ensure that the Server uses a compatible server software and have no
            // incompatible plugins
            getServer().getPluginManager().disablePlugin(this);
        } else if (!PaperLib.isPaper()) {
            getLogger().log(Level.WARNING, "#######################################################");
            getLogger().log(Level.WARNING, "");
            getLogger().log(Level.WARNING, "自 24/12/22 起 Slimefun4-Folia");
            getLogger().log(Level.WARNING, "转为 Paper 插件, 你必须要使用 Paper");
            getLogger().log(Level.WARNING, "或其分支才可使用 Slimefun4-Folia.");
            getLogger().log(Level.WARNING, "立即下载 Paper: https://papermc.io/downloads/paper");
            getLogger().log(Level.WARNING, "");
            getLogger().log(Level.WARNING, "#######################################################");
            getServer().getPluginManager().disablePlugin(this);
        } else {
            // The Environment has been validated.
            try {
                onPluginStart();
            } catch (Exception | LinkageError x) {
                /*
                 * 启动过程抛异常时, Bukkit 只会打印堆栈, 插件却仍然停留在 enabled 状态:
                 * 物品/机器只注册了一半甚至完全没注册, 但服务器看起来一切正常。
                 * 这里明确禁用插件, 让问题第一时间暴露出来。
                 */
                getLogger()
                        .log(Level.SEVERE, x, () -> "Slimefun 启动失败, 插件将被禁用! 若此前使用 PlugMan 等插件重载过 Slimefun, 请整服重启后再试");
                getServer().getPluginManager().disablePlugin(this);
            }
        }
    }

    /**
     * This is our start method for a Unit Test environment.
     */
    private void onUnitTestStart() {
        local = new LocalizationService(this, "", null);
        networkManager = new NetworkManager(200);
        command.register();
        cfgManager.load();
        registry.load(this);
        loadTags();
        soundService.reload(false);
    }

    /**
     * This is our start method for a correct Slimefun installation.
     */
    private void onPluginStart() {
        long timestamp = System.nanoTime();
        Logger logger = getLogger();

        warnIfReloadUnsupported(logger);

        // Check if CS-CoreLib is installed (it is no longer needed)
        if (getServer().getPluginManager().getPlugin("CS-CoreLib") != null) {
            StartupWarnings.discourageCSCoreLib(logger);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Encourage newer Java version
        if (NumberUtils.getJavaVersion() < RECOMMENDED_JAVA_VERSION) {
            StartupWarnings.oldJavaVersion(logger, RECOMMENDED_JAVA_VERSION);
        }

        // If the server has no "data-storage" folder, it's _probably_ a new install. So mark it for
        // metrics.
        isNewlyInstalled = !new File("data-storage/Slimefun").exists();

        // Creating all necessary Folders
        logger.log(Level.INFO, "正在创建文件夹...");
        createDirectories();

        // Load various config settings into our cache
        cfgManager.load();
        registry.load(this);

        logger.log(Level.INFO, "正在加载数据库...");
        if (PlayerProfileMigrator.getInstance().hasOldData()
                || BlockStorageMigrator.getInstance().hasOldData()) {
            Slimefun.logger().warning("====================================================");
            Slimefun.logger().warning("\n");
            Slimefun.logger().log(Level.WARNING, "!!! 检测到使用文件储存的旧玩家数据 !!!");
            Slimefun.logger().warning("请在服务器加载完成后, 使用 /sf migrate confirm 进行迁移!");
            Slimefun.logger().warning("如果不迁移, 旧版本的数据将会失效!!!");
            Slimefun.logger().warning("\n");
            Slimefun.logger().warning("需要使用数据库的用户, 请关服后在以下配置文件中配置数据库:");
            Slimefun.logger().warning("block-storage.yml 和 profile-storage.yml");
            Slimefun.logger().warning("\n");
            Slimefun.logger().warning("====================================================");
        }
        databaseManager.init();

        if (!databaseManager.isInitialized()) {
            logger.log(Level.SEVERE, "数据存储初始化失败, Slimefun 无法继续启动! 若此前使用 PlugMan 等插件重载过 Slimefun, 请整服重启后再试");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Set up localization
        logger.log(Level.INFO, "正在加载语言文件...");

        var config = cfgManager.getPluginConfig();
        String chatPrefix = config.getString("options.chat-prefix");
        String serverDefaultLanguage = config.getString("options.language");
        local = new LocalizationService(this, chatPrefix, serverDefaultLanguage);

        int networkSize = config.getInt("networks.max-size");

        // Make sure that the network size is a valid input
        if (networkSize < 1) {
            logger.log(Level.WARNING, "'networks.max-size' 大小设置错误! 它必须大于1, 而你设置的是: {0}", networkSize);
            networkSize = 1;
        }

        networkManager = new NetworkManager(
                networkSize,
                config.getBoolean("networks.enable-visualizer"),
                config.getBoolean("networks.delete-excess-items"));

        // Setting up bStats and analytics
        new Thread(metricsService::start, "Slimefun Metrics").start();
        analyticsService.start();

        // Registering all GEO Resources
        logger.log(Level.INFO, "加载矿物资源...");
        GEOResourcesSetup.setup();

        logger.log(Level.INFO, "加载自定义标签...");
        loadTags();

        logger.log(Level.INFO, "加载物品...");
        loadItems();

        logger.log(Level.INFO, "物品加载完成: {0} 个 Slimefun 物品, {1} 个多方块结构", new Object[] {
            registry.getAllSlimefunItems().size(), registry.getMultiBlocks().size()
        });

        logger.log(Level.INFO, "加载研究项目...");
        loadResearches();

        PostSetup.setupWiki();

        // All Slimefun Listeners
        logger.log(Level.INFO, "正在注册监听器...");

        // Inject downstream extra staff
        SlimefunExtended.init(this);

        registerListeners();

        // Initiating various Stuff and all items with a slight delay (0ms after the Server finished
        // loading)
        runSync(
                new SlimefunStartupTask(this, () -> {
                    sqlProfiler.initSlowSqlCheck(this);
                    textureService.register(registry.getAllSlimefunItems(), true);
                    permissionsService.update(registry.getAllSlimefunItems(), true);
                    soundService.reload(true);

                    // This try/catch should prevent buggy Spigot builds from blocking item loading
                    try {
                        recipeService.refresh();
                    } catch (Exception | LinkageError x) {
                        logger.log(
                                Level.SEVERE,
                                x,
                                () -> "An Exception occured while iterating through the Recipe list on Minecraft"
                                        + " Version "
                                        + minecraftVersion.getName()
                                        + " (Slimefun v"
                                        + getVersion()
                                        + ")");
                    }
                }),
                0);

        // Setting up our commands
        try {
            command.register();
        } catch (Exception | LinkageError x) {
            logger.log(Level.SEVERE, "An Exception occurred while registering the /slimefun command", x);
        }

        // Armor Update Task
        if (config.getBoolean("options.enable-armor-effects")) {
            new SlimefunArmorTask().schedule(this, config.getInt("options.armor-update-interval") * 20L);
            if (config.getBoolean("options.enable-radiation")) {
                new RadiationTask().schedule(this, config.getInt("options.radiation-update-interval") * 20L);
            }
            new RainbowArmorTask().schedule(this, config.getInt("options.rainbow-armor-update-interval") * 20L);
            new SolarHelmetTask().schedule(this, config.getInt("options.armor-update-interval") * 20L);
        } else if (config.getBoolean("options.enable-radiation")) {
            logger.log(Level.WARNING, "Cannot enable radiation while armor effects are disabled.");
        }

        // Starting our tasks
        // 自动保存: 周期由 options.auto-save-delay-in-minutes 控制 (分钟, <=0 表示关闭)
        autoSavingService.start(this, config.getInt("options.auto-save-delay-in-minutes"));
        hologramsService.start();
        ticker.start(this);

        logger.log(Level.INFO, "正在加载第三方插件支持...");
        integrations.start();

        gitHubService.start(this);

        if (cfgManager.isAutoUpdate()) {
            // 自动更新
            Bukkit.getAsyncScheduler()
                    .runDelayed(
                            this,
                            scheduledTask -> new AutoUpdateTask(this, getFile()).run(),
                            0L,
                            java.util.concurrent.TimeUnit.MILLISECONDS);
        }

        // Hooray!
        logger.log(Level.INFO, "Slimefun 完成加载, 耗时 {0}", getStartupTime(timestamp));
    }

    @Override
    public JavaPlugin getJavaPlugin() {
        return this;
    }

    @Override
    public String getBugTrackerURL() {
        return "https://github.com/LiuyeStudio/Slimefun4-Folia/issues";
    }

    @Override
    public String getWikiURL() {
        return "https://slimefun-wiki.guizhanss.cn/{0}";
    }

    /**
     * This method gets called when the {@link Plugin} gets disabled.
     * Most often it is called when the {@link Server} is shutting down or reloading.
     */
    @Override
    public void onDisable() {
        // Slimefun never loaded successfully, so we don't even bother doing stuff here
        if (instance() == null || minecraftVersion == MinecraftVersion.UNIT_TEST) {
            return;
        }

        SlimefunExtended.shutdown();
        getSQLProfiler().shutdown();

        // Cancel all tasks from this plugin immediately
        // Folia: CraftScheduler 是空壳，cancelTasks 会抛 UnsupportedOperationException。
        // 注意：RegionScheduler / EntityScheduler 这两种调度器在 API 层面
        // 根本没有 cancelTasks（javap 确认过），只有 AsyncScheduler 和
        // GlobalRegionScheduler 有。区域/实体的任务由 ScheduledTask#cancel() 或
        // 插件卸载时自动取消，这里不要乱加。
        getServer().getAsyncScheduler().cancelTasks(this);
        getServer().getGlobalRegionScheduler().cancelTasks(this);

        // Finishes all started movements/removals of block data
        ticker.setPaused(true);
        ticker.halt();
        /**try {
         * ticker.halt();
         * ticker.run();
         * } catch (Exception x) {
         * getLogger()
         * .log(
         * Level.SEVERE,
         * x,
         * () -> "Something went wrong while disabling the ticker task for Slimefun v"
         * + getDescription().getVersion());
         * }*/

        // Kill our Profiler Threads
        profiler.kill();

        // Save all Player Profiles that are still in memory
        PlayerProfile.iterator().forEachRemaining(profile -> {
            if (profile.isDirty()) {
                profile.save();
            }
        });

        try {
            databaseManager.shutdown();
        } catch (Exception | LinkageError x) {
            // 初始化失败 (例如热重载后数据库文件仍被旧实例占用) 时也要保证关闭流程继续执行
            getLogger().log(Level.SEVERE, "关闭数据存储时出现异常", x);
        }

        // Create a new backup zip
        if (cfgManager.getPluginConfig().getBoolean("options.backup-data")) {
            backupService.run();
        }

        // Close and unload any resources from our Metrics Service
        metricsService.cleanUp();

        // Terminate our Plugin instance
        setInstance(null);

        /**
         * Close all inventories on the server to prevent item dupes
         * (Incase some idiot uses /reload)
         *
         * Folia: 关闭界面是实体操作, 关闭阶段不能再从关闭线程直接操作玩家,
         * 因此逐个投递到玩家所在区域的调度器; 玩家已被移除时任务会被安全丢弃。
         *
         * 服务器整体关服时 (RegionShutdownThread) 不能关界面: 此时世界数据已在
         * 拆除中, ContainerOpenersCounter.scheduleRecheck 会向已无 WorldData 的
         * 世界调度方块 tick 并抛 NPE (ServerLevel.getCurrentWorldData() == null),
         * 而且玩家马上就要被踢出, 关闭界面已无意义, 直接跳过。
         */
        if (!getServer().isStopping()) {
            /*
             * Folia: 关服/重载时本方法不一定运行在全局区域线程上, 此时读取玩家列表会抛
             * UnsupportedOperationException。玩家马上就要被踢出, 关不掉界面也不影响数据安全,
             * 因此这里只做兜底捕获, 避免拖垮整个 onDisable。
             */
            try {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    try {
                        if (Bukkit.isOwnedByCurrentRegion(p)) {
                            p.closeInventory();
                        } else {
                            p.getScheduler()
                                    .run(
                                            this,
                                            task -> {
                                                try {
                                                    p.closeInventory();
                                                } catch (Exception x) {
                                                    // 关服/世界拆除期间关闭界面可能失败, 忽略即可
                                                }
                                            },
                                            null);
                        }
                    } catch (Exception x) {
                        getLogger().log(Level.WARNING, "无法为玩家 " + p.getName() + " 关闭界面", x);
                    }
                }
            } catch (UnsupportedOperationException x) {
                getLogger().log(Level.FINE, "当前线程无法读取在线玩家列表, 跳过关闭界面");
            }
        }
    }

    /**
     * This is a private internal method to set the de-facto instance of {@link Slimefun}.
     * Having this as a seperate method ensures the seperation between static and non-static fields.
     * It also makes sonarcloud happy :)
     * Only ever use it during {@link #onEnable()} or {@link #onDisable()}.
     *
     * @param pluginInstance Our instance of {@link Slimefun} or null
     */
    private static void setInstance(@Nullable Slimefun pluginInstance) {
        instance = pluginInstance;
    }

    /**
     * This private method gives us a {@link Collection} of every {@link MinecraftVersion}
     * that Slimefun is compatible with (as a {@link String} representation).
     * <p>
     * Example:
     *
     * <pre>
     * { 1.14.x, 1.15.x, 1.16.x }
     * </pre>
     *
     * @return A {@link Collection} of all compatible minecraft versions as strings
     */
    static @Nonnull Collection<String> getSupportedVersions() {
        List<String> list = new ArrayList<>();

        for (MinecraftVersion version : MinecraftVersion.values()) {
            if (!version.isVirtual()) {
                list.add(version.getName());
            }
        }

        return list;
    }

    /**
     * This returns the {@link Logger} instance that Slimefun uses.
     * <p>
     * <strong>Any {@link SlimefunAddon} should use their own {@link Logger} instance!</strong>
     *
     * @return Our {@link Logger} instance
     */
    public static @Nonnull Logger logger() {
        validateInstance();
        return instance.getLogger();
    }

    /**
     * This method checks for the {@link MinecraftVersion} of the {@link Server}.
     * If the version is unsupported, a warning will be printed to the console.
     *
     * @return Whether the {@link MinecraftVersion} is unsupported
     */
    private boolean isVersionUnsupported() {
        try {
            // First check if they still use the unsupported CraftBukkit software.
            if (!PaperLib.isSpigot() && Bukkit.getName().equals("CraftBukkit")) {
                StartupWarnings.invalidServerSoftware(getLogger());
                return true;
            }

            // Now check the actual Version of Minecraft
            // the Minecraft version id (e.g. "1.20.4", "1.20.2-pre2", "23w31a")
            ServerVersion serverVerDetail = SlimefunExtended.getServerVerDetail(getServer());

            if (serverVerDetail == null) {
                getLogger()
                        .log(
                                Level.WARNING,
                                "我们无法识别你正在使用的 Minecraft 版本 ({0})",
                                getServer().getMinecraftVersion());
                return false;
            }

            int major = serverVerDetail.getMajor();
            int minor = serverVerDetail.getMinor();
            int patch = serverVerDetail.getPatch();

            // Check all supported versions of Minecraft
            for (MinecraftVersion supportedVersion : MinecraftVersion.values()) {
                if (supportedVersion.isMinecraftVersion(major, minor, patch)) {
                    minecraftVersion = supportedVersion;
                    return false;
                }
            }

            // Looks like you are using an unsupported Minecraft Version
            StartupWarnings.invalidMinecraftVersion(
                    getLogger(),
                    getServer().getMinecraftVersion(),
                    getDescription().getVersion());
            return true;
        } catch (Exception | LinkageError x) {
            getLogger()
                    .log(
                            Level.SEVERE,
                            x,
                            () -> "错误: 无法识别服务器 Minecraft 版本, Slimefun v"
                                    + getDescription().getVersion());

            // We assume "unsupported" if something went wrong.
            return true;
        }
    }

    /**
     * This returns our {@link GPSNetwork} instance.
     * The {@link GPSNetwork} is responsible for handling any GPS-related
     * operations and for managing any {@link GEOResource}.
     *
     * @return Our {@link GPSNetwork} instance
     */
    public static @Nonnull GPSNetwork getGPSNetwork() {
        validateInstance();
        return instance.gpsNetwork;
    }

    /**
     * This method creates all necessary directories (and sub directories) for Slimefun.
     */
    private void createDirectories() {
        String[] storageFolders = {"waypoints", "block-backups"};
        String[] pluginFolders = {"scripts", "error-reports", "cache/github", "world-settings"};

        for (String folder : storageFolders) {
            File file = new File("data-storage/Slimefun", folder);

            if (!file.exists()) {
                file.mkdirs();
            }
        }

        for (String folder : pluginFolders) {
            File file = new File("plugins/Slimefun", folder);

            if (!file.exists()) {
                file.mkdirs();
            }
        }
    }

    /**
     * This method registers all of our {@link Listener Listeners}.
     */
    private void registerListeners() {
        chatCatcher = new PlayerChatCatcher(this);
        // Old deprecated CS-CoreLib Listener
        new MenuListener(this);

        new SlimefunBootsListener(this);
        new SlimefunItemInteractListener(this);
        new SlimefunItemConsumeListener(this);
        new BlockPhysicsListener(this);
        new CargoNodeListener(this);
        new MultiBlockListener(this);
        new GadgetsListener(this);
        new DispenserListener(this);
        new BlockListener(this);
        new EnhancedFurnaceListener(this);
        new ItemPickupListener(this);
        new ItemDropListener(this);
        new DeathpointListener(this);
        new ExplosionsListener(this);
        new DebugFishListener(this);
        new FireworksListener(this);
        new WitherListener(this);
        new IronGolemListener(this);
        new EntityInteractionListener(this);
        new MobDropListener(this);
        new VillagerTradingListener(this);
        new ElytraImpactListener(this);
        new CraftingTableListener(this);
        new AnvilListener(this);
        new BrewingStandListener(this);
        new CauldronListener(this);
        new GrindstoneListener(this);
        new CartographyTableListener(this);
        new ButcherAndroidListener(this);
        new MiningAndroidListener(this);
        new NetworkListener(this, networkManager);
        new HopperListener(this);
        new TalismanListener(this);
        new SoulboundListener(this);
        new AutoCrafterListener(this);
        new SlimefunItemHitListener(this);
        if (SlimefunExtended.isAtLeast(1, 21, 5)) {
            new VersionedMiddleClickListener(this);
        } else {
            new MiddleClickListener(this);
        }
        new BeeListener(this);
        new BeeWingsListener(this, (BeeWings) SlimefunItems.BEE_WINGS.getItem());
        new PiglinListener(this);
        new SmithingTableListener(this);
        new VanillaCrafterListener(this);
        new JoinListener(this);

        // Item-specific Listeners
        new CoolerListener(this, (Cooler) SlimefunItems.COOLER.getItem());
        new SeismicAxeListener(this, (SeismicAxe) SlimefunItems.SEISMIC_AXE.getItem());
        new RadioactivityListener(this);
        new AncientAltarListener(this, (AncientAltar) SlimefunItems.ANCIENT_ALTAR.getItem(), (AncientPedestal)
                SlimefunItems.ANCIENT_PEDESTAL.getItem());
        grapplingHookListener.register(this, (GrapplingHook) SlimefunItems.GRAPPLING_HOOK.getItem());
        bowListener.register(this);
        backpackListener.register(this);

        // Handle Slimefun Guide being given on Join
        new SlimefunGuideListener(this, cfgManager.getPluginConfig().getBoolean("guide.receive-on-first-join"));

        // Handle the "Book + Slimeball" recipe for the Slimefun Guide
        new SlimefunGuideRecipeListener(this);

        // Clear the Slimefun Guide History upon Player Leaving
        new PlayerProfileListener(this);
    }

    /**
     * This (re)loads every {@link SlimefunTag}.
     */
    private void loadTags() {
        for (SlimefunTag tag : SlimefunTag.values()) {
            try {
                // Only reload "empty" (or unloaded) Tags
                if (tag.isEmpty()) {
                    tag.reload();
                }
            } catch (TagMisconfigurationException e) {
                getLogger().log(Level.SEVERE, e, () -> "Failed to load Tag: " + tag.name());
            }
        }
    }

    /**
     * This loads all of our items.
     */
    private void loadItems() {
        try {
            SlimefunItemSetup.setup(this);
        } catch (Exception | LinkageError x) {
            int registered = registry.getAllSlimefunItems().size();

            getLogger()
                    .log(
                            Level.SEVERE,
                            x,
                            () -> "An Error occurred while initializing SlimefunItems for Slimefun " + getVersion()
                                    + " (已成功注册 " + registered + " 个物品)");

            if (registered == 0) {
                // 一个物品都没注册就等于没有 Slimefun, 继续运行只会让用户面对一堆"不是 Slimefun 物品"的报错
                getLogger().log(Level.SEVERE, "没有成功注册任何 Slimefun 物品, 插件将被禁用!");
                getServer().getPluginManager().disablePlugin(this);
            }
        }
    }

    /**
     * Slimefun 的全部运行状态 (物品注册表, 多方块机器, 数据库连接池, ticker) 都绑定在插件实例上,
     * 而 PlugMan 一类的重载插件只会重新执行一次 {@link #onEnable()}, 且 Folia 上旧实例的区域任务
     * 无法取消, 因此热重载后通常表现为物品/机器没有注册。这里在加载时给出明确提示。
     *
     * @param logger
     *            用于输出提示的 {@link Logger}
     */
    private void warnIfReloadUnsupported(@Nonnull Logger logger) {
        var pluginManager = getServer().getPluginManager();

        for (String name : new String[] {"PlugManX", "PlugMan"}) {
            if (pluginManager.getPlugin(name) != null) {
                logger.log(Level.WARNING, "#######################################################");
                logger.log(Level.WARNING, "检测到重载插件: {0}", name);
                logger.log(Level.WARNING, "Slimefun 不支持热重载, 请勿对 Slimefun 使用 reload 命令!");
                logger.log(Level.WARNING, "重载后物品/机器可能无法正确注册, 更换插件版本后请整服重启.");
                logger.log(Level.WARNING, "#######################################################");
                return;
            }
        }
    }

    /**
     * This loads our researches.
     */
    private void loadResearches() {
        try {
            ResearchSetup.setupResearches();
        } catch (Exception | LinkageError x) {
            getLogger()
                    .log(
                            Level.SEVERE,
                            x,
                            () -> "An Error occurred while initializing Slimefun Researches for Slimefun "
                                    + getVersion());
        }
    }

    /**
     * This returns the global instance of {@link Slimefun}.
     * This may return null if the {@link Plugin} was disabled.
     *
     * @return The {@link Slimefun} instance
     */
    public static @Nullable Slimefun instance() {
        return instance;
    }

    /**
     * This private static method allows us to throw a proper {@link Exception}
     * whenever someone tries to access a static method while the instance is null.
     * This happens when the method is invoked before {@link #onEnable()} or after {@link #onDisable()}.
     * <p>
     * Use it whenever a null check is needed to avoid a non-descriptive {@link NullPointerException}.
     */
    private static void validateInstance() {
        if (instance == null) {
            throw new IllegalStateException("Cannot invoke static method, Slimefun instance is null.");
        }
    }

    /**
     * This method checks whether a {@link Slimefun} instance is currently available.
     * <p>
     * A {@link Slimefun} instance is available from the very beginning of {@link #onEnable()}
     * until the very end of {@link #onDisable()}. It is <b>not</b> available if Slimefun was
     * disabled, e.g. because the server version or environment is unsupported, or because
     * Slimefun failed to start.
     * <p>
     * Addons and third-party plugins should check this method before calling any other static
     * accessor of this class, since those accessors throw an {@link IllegalStateException}
     * when no instance is available.
     *
     * @return Whether a {@link Slimefun} instance is currently available
     */
    public static boolean isInstanceAvailable() {
        return instance != null;
    }

    /**
     * This method returns out {@link MinecraftRecipeService} for Slimefun.
     * This service is responsible for finding/identifying {@link Recipe Recipes}
     * from vanilla Minecraft.
     *
     * @return Slimefun's {@link MinecraftRecipeService} instance
     */
    public static @Nonnull MinecraftRecipeService getMinecraftRecipeService() {
        validateInstance();
        return instance.recipeService;
    }

    /**
     * This returns the version of Slimefun that is currently installed.
     *
     * @return The currently installed version of Slimefun
     */
    public static @Nonnull String getVersion() {
        validateInstance();
        return instance.getDescription().getVersion();
    }

    public static @Nonnull Config getCfg() {
        validateInstance();
        return instance.cfgManager.getPluginConfig();
    }

    public static @Nonnull Config getResearchCfg() {
        validateInstance();
        return instance.researches;
    }

    public static @Nonnull Config getItemCfg() {
        validateInstance();
        return instance.items;
    }

    /**
     * This method returns out world settings service.
     * That service is responsible for managing item settings per
     * {@link World}, such as disabling a {@link SlimefunItem} in a
     * specific {@link World}.
     *
     * @return Our instance of {@link PerWorldSettingsService}
     */
    public static @Nonnull PerWorldSettingsService getWorldSettingsService() {
        validateInstance();
        return instance.worldSettingsService;
    }

    public static @Nonnull TickerTask getTickerTask() {
        validateInstance();
        return instance.ticker;
    }

    /**
     * This returns the {@link LocalizationService} of Slimefun.
     *
     * @return The {@link LocalizationService} of Slimefun
     */
    public static @Nonnull LocalizationService getLocalization() {
        validateInstance();
        return instance.local;
    }

    /**
     * This returns our {@link HologramsService} which handles the creation and
     * cleanup of any holograms.
     *
     * @return Our instance of {@link HologramsService}
     */
    public static @Nonnull HologramsService getHologramsService() {
        validateInstance();
        return instance.hologramsService;
    }

    public static @Nonnull CustomItemDataService getItemDataService() {
        validateInstance();
        return instance.itemDataService;
    }

    public static @Nonnull CustomTextureService getItemTextureService() {
        validateInstance();
        return instance.textureService;
    }

    public static @Nonnull PermissionsService getPermissionsService() {
        validateInstance();
        return instance.permissionsService;
    }

    public static @Nonnull BlockDataService getBlockDataService() {
        validateInstance();
        return instance.blockDataService;
    }

    public static @Nonnull ItemStackService getItemStackService() {
        validateInstance();
        return instance.itemStackService;
    }

    /**
     * This returns our instance of {@link IntegrationsManager}.
     * This is responsible for managing any integrations with third party {@link Plugin plugins}.
     *
     * @return Our instance of {@link IntegrationsManager}
     */
    public static @Nonnull IntegrationsManager getIntegrations() {
        validateInstance();
        return instance.integrations;
    }

    /**
     * This returns out instance of the {@link ProtectionManager}.
     * This bridge is used to hook into any third-party protection {@link Plugin}.
     *
     * @return Our instanceof of the {@link ProtectionManager}
     */
    public static @Nonnull ProtectionManager getProtectionManager() {
        return getIntegrations().getProtectionManager();
    }

    /**
     * This returns our {@link  SoundService} which handles the configuration of all sounds used in Slimefun
     *
     * @return Our instance of {@link SoundService}
     */
    @Nonnull
    public static SoundService getSoundService() {
        validateInstance();
        return instance.soundService;
    }

    /**
     * This returns our {@link NetworkManager} which is responsible
     * for handling the Cargo and Energy networks.
     *
     * @return Our {@link NetworkManager} instance
     */
    public static @Nonnull NetworkManager getNetworkManager() {
        validateInstance();
        return instance.networkManager;
    }

    /**
     * This returns the time it took to load Slimefun (given a starting point).
     *
     * @param timestamp The time at which we started to load Slimefun.
     * @return The total time it took to load Slimefun (in ms or s)
     */
    private @Nonnull String getStartupTime(long timestamp) {
        long ms = (System.nanoTime() - timestamp) / 1000000;

        if (ms > 1000) {
            return NumberUtils.roundDecimalNumber(ms / 1000.0) + 's';
        } else {
            return NumberUtils.roundDecimalNumber(ms) + "ms";
        }
    }

    /**
     * This method returns the {@link UpdaterService} of Slimefun.
     * It is used to handle automatic updates.
     *
     * @return The {@link UpdaterService} for Slimefun
     */
    public static @Nonnull UpdaterService getUpdater() {
        validateInstance();
        return instance.updaterService;
    }

    /**
     * This method returns the {@link MetricsService} of Slimefun.
     * It is used to handle sending metric information to bStats.
     *
     * @return The {@link MetricsService} for Slimefun
     */
    public static @Nonnull MetricsService getMetricsService() {
        validateInstance();
        return instance.metricsService;
    }

    /**
     * This method returns the {@link AnalyticsService} of Slimefun.
     * It is used to handle sending analytic information.
     *
     * @return The {@link AnalyticsService} for Slimefun
     */
    public static @Nonnull AnalyticsService getAnalyticsService() {
        validateInstance();
        return instance.analyticsService;
    }

    /**
     * This method returns the {@link GitHubService} of Slimefun.
     * It is used to retrieve data from GitHub repositories.
     *
     * @return The {@link GitHubService} for Slimefun
     */
    public static @Nonnull GitHubService getGitHubService() {
        validateInstance();
        return instance.gitHubService;
    }

    /**
     * This method checks if this is currently running in a unit test
     * environment.
     *
     * @return Whether we are inside a unit test
     */
    public boolean isUnitTest() {
        return minecraftVersion == MinecraftVersion.UNIT_TEST;
    }

    public static @Nonnull SlimefunConfigManager getConfigManager() {
        validateInstance();
        return instance.cfgManager;
    }

    public static @Nonnull SlimefunDatabaseManager getDatabaseManager() {
        validateInstance();
        return instance.databaseManager;
    }

    public static @Nonnull SlimefunRegistry getRegistry() {
        validateInstance();
        return instance.registry;
    }

    public static @Nonnull GrapplingHookListener getGrapplingHookListener() {
        validateInstance();
        return instance.grapplingHookListener;
    }

    public static @Nonnull BackpackListener getBackpackListener() {
        validateInstance();
        return instance.backpackListener;
    }

    public static @Nonnull SlimefunBowListener getBowListener() {
        validateInstance();
        return instance.bowListener;
    }

    /**
     * The {@link Command} that was added by Slimefun.
     *
     * @return Slimefun's command
     */
    public static @Nonnull SlimefunCommand getCommand() {
        validateInstance();
        return instance.command;
    }

    /**
     * This returns our instance of the {@link SlimefunProfiler}, a tool that is used
     * to analyse performance and lag.
     *
     * @return The {@link SlimefunProfiler}
     */
    public static @Nonnull SlimefunProfiler getProfiler() {
        validateInstance();
        return instance.profiler;
    }

    public static @Nonnull SQLProfiler getSQLProfiler() {
        validateInstance();
        return instance.sqlProfiler;
    }

    /**
     * This returns the currently installed version of Minecraft.
     *
     * @return The current version of Minecraft
     */
    public static @Nonnull MinecraftVersion getMinecraftVersion() {
        validateInstance();
        return instance.minecraftVersion;
    }

    /**
     * This method returns whether this version of Slimefun was newly installed.
     * It will return true if this {@link Server} uses Slimefun for the very first time.
     *
     * @return Whether this is a new installation of Slimefun
     */
    public static boolean isNewlyInstalled() {
        validateInstance();
        return instance.isNewlyInstalled;
    }

    /**
     * This method returns a {@link Set} of every {@link Plugin} that lists Slimefun
     * as a required or optional dependency.
     * <p>
     * We will just assume this to be a list of our addons.
     *
     * @return A {@link Set} of every {@link Plugin} that is dependent on Slimefun
     */
    public static @Nonnull Set<Plugin> getInstalledAddons() {
        validateInstance();
        String pluginName = instance.getName();

        // @formatter:off - Collect any Plugin that (soft)-depends on Slimefun
        return Arrays.stream(instance.getServer().getPluginManager().getPlugins())
                .filter(plugin -> {
                    PluginDescriptionFile description = plugin.getDescription();
                    return description.getDepend().contains(pluginName)
                            || description.getSoftDepend().contains(pluginName);
                })
                .collect(Collectors.toSet());
        // @formatter:on
    }

    /**
     * This method schedules a task for Slimefun on the <b>global region</b> thread.
     * <strong>For Slimefun only, not for addons.</strong>
     * <p>
     * The global region thread does <b>not</b> own any world, block or entity, so the task
     * <b>must not</b> touch any of those. Use {@link #runAtRegion(Location, Runnable)} for block
     * related work and {@link #runAtEntity(Entity, Runnable)} for entity related work.
     *
     * @param runnable
     *            The {@link Runnable} to run
     * @param delay
     *            The delay for this task, in ticks
     *
     * @return The resulting {@link ScheduledTask} or null if Slimefun was disabled
     */
    public static @Nullable ScheduledTask runSync(@Nonnull Runnable runnable, long delay) {
        Validate.notNull(runnable, "Cannot run null");
        Validate.isTrue(delay >= 0, "The delay cannot be negative");

        // Run the task instantly within a Unit Test
        if (getMinecraftVersion() == MinecraftVersion.UNIT_TEST) {
            runnable.run();
            return null;
        }

        if (instance == null || !instance.isEnabled()) {
            return null;
        }

        if (delay <= 0) {
            return Bukkit.getGlobalRegionScheduler().run(instance, task -> runnable.run());
        } else {
            return Bukkit.getGlobalRegionScheduler().runDelayed(instance, task -> runnable.run(), delay);
        }
    }

    /**
     * This method schedules a task for Slimefun on the <b>global region</b> thread.
     * <strong>For Slimefun only, not for addons.</strong>
     * <p>
     * The global region thread does <b>not</b> own any world, block or entity, so the task
     * <b>must not</b> touch any of those. Use {@link #runAtRegion(Location, Runnable)} for block
     * related work and {@link #runAtEntity(Entity, Runnable)} for entity related work.
     *
     * @param runnable
     *            The {@link Runnable} to run
     *
     * @return The resulting {@link ScheduledTask} or null if Slimefun was disabled
     */
    public static @Nullable ScheduledTask runSync(@Nonnull Runnable runnable) {
        return runSync(runnable, 0L);
    }

    /**
     * This method schedules a task for Slimefun on the thread which owns the region of the given
     * {@link Location}. This is the only valid thread to read or modify blocks and block states.
     * <strong>For Slimefun only, not for addons.</strong>
     * <p>
     * If the current thread already owns that region, the task is executed immediately instead of
     * being deferred by a tick.
     *
     * @param location
     *            The {@link Location} whose region should run the task
     * @param runnable
     *            The {@link Runnable} to run
     *
     * @return The resulting {@link ScheduledTask} or null if the task ran immediately or Slimefun was disabled
     */
    public static @Nullable ScheduledTask runAtRegion(@Nonnull Location location, @Nonnull Runnable runnable) {
        return runAtRegion(location, runnable, 0L);
    }

    /**
     * This method schedules a delayed task for Slimefun on the thread which owns the region of the
     * given {@link Location}.
     * <strong>For Slimefun only, not for addons.</strong>
     *
     * @param location
     *            The {@link Location} whose region should run the task
     * @param runnable
     *            The {@link Runnable} to run
     * @param delay
     *            The delay for this task, in ticks
     *
     * @return The resulting {@link ScheduledTask} or null if Slimefun was disabled
     */
    public static @Nullable ScheduledTask runAtRegion(
            @Nonnull Location location, @Nonnull Runnable runnable, long delay) {
        Validate.notNull(location, "Cannot run at a null location");
        Validate.notNull(runnable, "Cannot run null");
        Validate.isTrue(delay >= 0, "The delay cannot be negative");

        if (getMinecraftVersion() == MinecraftVersion.UNIT_TEST) {
            runnable.run();
            return null;
        }

        if (instance == null || !instance.isEnabled()) {
            return null;
        }

        /*
         * 世界可能已经被卸载 (例如持久化位置所在的世界不再加载), 此时没有可用的区域,
         * 直接放弃而不是抛出 NullPointerException。
         */
        if (location.getWorld() == null) {
            return null;
        }

        if (delay <= 0) {
            // Already on the owning region thread: run right away to keep the original timing
            try {
                if (Bukkit.isOwnedByCurrentRegion(location)) {
                    runnable.run();
                    return null;
                }

                return Bukkit.getRegionScheduler().run(instance, location, task -> runnable.run());
            } catch (IllegalStateException x) {
                // 区块/世界已卸载时区域调度器会拒绝任务
                return null;
            }
        }

        try {
            return Bukkit.getRegionScheduler().runDelayed(instance, location, task -> runnable.run(), delay);
        } catch (IllegalStateException x) {
            return null;
        }
    }

    /**
     * This method schedules a task for Slimefun on the thread which owns the given {@link Entity}.
     * This is the only valid thread to read or modify an entity, its inventory, its potion effects
     * or to send it messages.
     * <strong>For Slimefun only, not for addons.</strong>
     * <p>
     * If the current thread already owns that entity's region, the task is executed immediately
     * instead of being deferred by a tick.
     *
     * @param entity
     *            The {@link Entity} whose region should run the task
     * @param runnable
     *            The {@link Runnable} to run
     *
     * @return The resulting {@link ScheduledTask} or null if the task ran immediately or Slimefun was disabled
     */
    public static @Nullable ScheduledTask runAtEntity(@Nonnull Entity entity, @Nonnull Runnable runnable) {
        return runAtEntity(entity, runnable, 0L);
    }

    /**
     * This method schedules a delayed task for Slimefun on the thread which owns the given
     * {@link Entity}. The task is dropped if the {@link Entity} is removed in the meantime.
     * <strong>For Slimefun only, not for addons.</strong>
     *
     * @param entity
     *            The {@link Entity} whose region should run the task
     * @param runnable
     *            The {@link Runnable} to run
     * @param delay
     *            The delay for this task, in ticks
     *
     * @return The resulting {@link ScheduledTask} or null if Slimefun was disabled
     */
    public static @Nullable ScheduledTask runAtEntity(@Nonnull Entity entity, @Nonnull Runnable runnable, long delay) {
        Validate.notNull(entity, "Cannot run at a null entity");
        Validate.notNull(runnable, "Cannot run null");
        Validate.isTrue(delay >= 0, "The delay cannot be negative");

        if (getMinecraftVersion() == MinecraftVersion.UNIT_TEST) {
            runnable.run();
            return null;
        }

        if (instance == null || !instance.isEnabled()) {
            return null;
        }

        if (delay <= 0) {
            // Already on the owning region thread: run right away to keep the original timing
            if (Bukkit.isOwnedByCurrentRegion(entity)) {
                runnable.run();
                return null;
            }

            return entity.getScheduler().run(instance, task -> runnable.run(), null);
        }

        return entity.getScheduler().runDelayed(instance, task -> runnable.run(), null, delay);
    }

    /**
     * This method schedules a task on a thread that is allowed to talk to the given
     * {@link CommandSender}: the owning entity thread for {@link Player players} and the global
     * region thread for everything else (console, command blocks, ...).
     * <strong>For Slimefun only, not for addons.</strong>
     *
     * @param sender
     *            The {@link CommandSender} the task will interact with
     * @param runnable
     *            The {@link Runnable} to run
     *
     * @return The resulting {@link ScheduledTask} or null if Slimefun was disabled
     */
    public static @Nullable ScheduledTask runForSender(@Nonnull CommandSender sender, @Nonnull Runnable runnable) {
        Validate.notNull(sender, "Cannot run for a null sender");
        Validate.notNull(runnable, "Cannot run null");

        if (sender instanceof Entity entity) {
            return runAtEntity(entity, runnable);
        }

        return runSync(runnable);
    }

    /**
     * This returns a thread-safe snapshot of every online {@link Player}.
     * <p>
     * Folia: {@link Server#getOnlinePlayers()} throws an {@link UnsupportedOperationException}
     * when it is called from a region thread, so code that may run on the global region thread,
     * a region thread or an asynchronous thread (such as the ticker) must use this method.
     *
     * @return An immutable {@link List} of the online {@link Player Players}
     */
    @Nonnull
    public static List<Player> getOnlinePlayersSnapshot() {
        Server server = Bukkit.getServer();

        if (server == null) {
            return Collections.emptyList();
        }

        try {
            return new ArrayList<>(server.getOnlinePlayers());
        } catch (UnsupportedOperationException x) {
            /*
             * Some Folia builds refuse this call from a region thread. Falling back to an empty
             * snapshot is always safe: every caller only uses it to broadcast informational messages.
             */
            return Collections.emptyList();
        }
    }

    @Nonnull
    public File getFile() {
        return super.getFile();
    }

    public static @Nonnull PlayerChatCatcher getChatCatcher() {
        validateInstance();
        return instance.chatCatcher;
    }

    /**
     * This method returns the {@link ThreadService} of Slimefun.
     * <b>Do not use this if you're an addon. Please make your own {@link ThreadService}.</b>
     *
     * @return The {@link ThreadService} for Slimefun
     */
    public static @Nonnull ThreadService getThreadService() {
        return instance().threadService;
    }
}
