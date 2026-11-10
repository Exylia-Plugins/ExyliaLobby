package net.exylia.exyliaLobby.module.onboarding;

import net.exylia.exyliaLobby.area.AreaCause;
import net.exylia.exyliaLobby.module.LobbyContext;
import net.exylia.exyliaLobby.module.LobbyModule;
import net.exylia.lib.api.ExyliaAPI;
import net.exylia.lib.api.journey.JourneyService;
import net.exylia.lib.api.journey.StartResult;
import net.exylia.lib.config.ConfigFile;
import net.exylia.lib.interaction.Click;
import net.exylia.lib.interaction.Interactions;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;

/**
 * Hands players who join the lobby for the first time to ExyliaJourney, through its public {@link JourneyService}.
 * Without ExyliaJourney it runs the configured fallback commands instead. Off by default.
 */
public final class OnboardingModule extends LobbyModule {

    public static final String ID = "onboarding";

    private ConfigFile<OnboardingSettings> settings;

    public OnboardingModule(@NotNull LobbyContext context) {
        super(ID, context);
    }

    @Override
    public @NotNull Material icon() {
        return Material.COMPASS;
    }

    @Override
    protected void registerConfigs() {
        settings = settings(OnboardingSettings.class);
    }

    @Override
    protected void start() {
    }

    @Override
    protected void enter(@NotNull Player player, @NotNull AreaCause cause) {
        if (cause != AreaCause.JOIN) return;
        OnboardingSettings now = settings.get();
        if (now.firstJoinOnly() && player.hasPlayedBefore()) return;
        long ticks = Math.max(1L, now.delay().toMillis() / 50L);
        context.tasks().runAtEntityLater(player, ticks, () -> handOver(player));
    }

    private void handOver(Player player) {
        OnboardingSettings now = settings.get();
        Optional<JourneyService> journeys = Bukkit.getPluginManager().isPluginEnabled("ExyliaJourney")
                ? ExyliaAPI.get(JourneyService.class) : Optional.empty();
        if (journeys.isPresent()) {
            StartResult result = journeys.get().start(player, now.journey());
            context.debug().debug("Onboarding: " + player.getName() + " -> journey " + now.journey() + ": " + result);
            return;
        }
        if (now.fallbackCommands().isEmpty()) return;
        Interactions.compile(plugin(), now.fallbackCommands(), "onboarding fallback").run(player, Click.LEFT, Map.of());
    }
}
