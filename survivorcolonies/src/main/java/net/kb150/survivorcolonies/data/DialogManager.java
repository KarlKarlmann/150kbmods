package net.kb150.survivorcolonies.data;

import com.mojang.logging.LogUtils;
import net.kb150.survivorcolonies.SurvivorColonies;
import net.kb150.survivorcolonies.entity.SurvivorEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Laufzeit-Manager fuer den komprimierten binaeren Konversations-Graphen (v5 SCDG).
 * Laedt 'dialog_logic.bin' aus dem Data-Asset-Pfad in wenigen Millisekunden ohne JSON-Overhead.
 */
public class DialogManager extends SimplePreparableReloadListener<byte[]> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation BINARY_LOCATION =
            new ResourceLocation(SurvivorColonies.MODID, "survivors/dialog_logic.bin");

    // Condition Types (aus space_converter.py)
    public static final int COND_NONE = 0;
    public static final int COND_TONE = 1;
    public static final int COND_BACKSTORY = 2;
    public static final int COND_MOTIVATION = 3;
    public static final int COND_TRUST = 4;
    public static final int COND_SKILL = 5;

    // Feste Standard-Mappings fuer numerische IDs
    private static final String[] KNOWN_TONES = {
            "grumpy", "panicked", "arrogant", "cheerful", "cynical", "mysterious"
    };
    private static final String[] KNOWN_BACKSTORIES = {
            "mine_collapse", "bandit_raider", "monster_ambush", "exile", "lost_caravan"
    };
    private static final String[] KNOWN_MOTIVATIONS = {
            "safety", "money", "purpose", "revenge", "food"
    };
    private static final String[] KNOWN_SKILLS = {
            "Strength", "Stamina", "Athletics", "Agility", "Focus"
    };

    // Dynamisch geladene Style-Tabelle aus dem Datei-Header
    private static String[] STYLE_TABLE = new String[0];

    private static final List<Integer> ROOTS = new ArrayList<>();
    private static final Map<Integer, NpcReaction> NPC_REACTIONS = new HashMap<>();
    private static final Map<Integer, PlayerReaction> PLAYER_REACTIONS = new HashMap<>();

    public DialogManager() {
    }

    @Override
    protected byte[] prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        profiler.startTick();
        profiler.push("read_dialog_logic_bin");

        Optional<Resource> res = resourceManager.getResource(BINARY_LOCATION);
        if (res.isEmpty()) {
            LOGGER.warn("[SurvivorColonies] 'dialog_logic.bin' nicht unter '{}' gefunden!", BINARY_LOCATION);
            profiler.pop();
            profiler.endTick();
            return null;
        }

        try (InputStream in = res.get().open()) {
            byte[] bytes = in.readAllBytes();
            profiler.pop();
            profiler.endTick();
            return bytes;
        } catch (Exception e) {
            LOGGER.error("[SurvivorColonies] Fehler beim Lesen von '{}': {}", BINARY_LOCATION, e.getMessage(), e);
            profiler.pop();
            profiler.endTick();
            return null;
        }
    }

    @Override
    protected void apply(byte[] rawCompressedData, ResourceManager resourceManager, ProfilerFiller profiler) {
        if (rawCompressedData == null || rawCompressedData.length == 0) {
            return;
        }

        long startTime = System.currentTimeMillis();

        ROOTS.clear();
        NPC_REACTIONS.clear();
        PLAYER_REACTIONS.clear();

        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(rawCompressedData)))) {
            // 1. Magic Bytes Check ('SCDG')
            byte[] magic = new byte[4];
            in.readFully(magic);
            String magicStr = new String(magic, StandardCharsets.US_ASCII);
            if (!"SCDG".equals(magicStr)) {
                throw new IllegalStateException("Ungueltige Magic Bytes in dialog_logic.bin: " + magicStr);
            }

            // 2. Format Version
            int version = in.readUnsignedShort();
            if (version != 5) {
                throw new IllegalStateException("Nicht unterstuetzte Dialog-Graph-Version: " + version + " (Erwartet: 5)");
            }

            // 3. Dynamische String-Tabelle fuer Dialog-Styles
            int styleCount = in.readUnsignedShort();
            STYLE_TABLE = new String[styleCount];
            for (int i = 0; i < styleCount; i++) {
                int strLen = in.readUnsignedShort();
                byte[] strBytes = new byte[strLen];
                in.readFully(strBytes);
                STYLE_TABLE[i] = new String(strBytes, StandardCharsets.UTF_8);
            }

            // 4. Roots
            int rootCount = in.readUnsignedShort();
            for (int i = 0; i < rootCount; i++) {
                ROOTS.add(in.readInt());
            }

            // 5. NPC Reactions
            int npcCount = in.readInt();
            for (int i = 0; i < npcCount; i++) {
                int id = in.readInt();
                int optCount = in.readUnsignedShort();
                List<Integer> options = new ArrayList<>(optCount);
                for (int j = 0; j < optCount; j++) {
                    options.add(in.readInt());
                }
                String textKey = "survivor.npc." + id;
                NPC_REACTIONS.put(id, new NpcReaction(id, textKey, options));
            }

            // 6. Player Reactions
            int playerCount = in.readInt();
            for (int i = 0; i < playerCount; i++) {
                int id = in.readInt();
                int styleId = in.readUnsignedShort();
                String style = (styleId >= 0 && styleId < STYLE_TABLE.length) ? STYLE_TABLE[styleId] : "neutral";

                int edgeCount = in.readUnsignedShort();
                List<Edge> edges = new ArrayList<>(edgeCount);

                for (int j = 0; j < edgeCount; j++) {
                    int targetId = in.readInt();
                    byte trustDelta = in.readByte();
                    int condType = in.readUnsignedByte();

                    Condition condition = parseCondition(condType, in);
                    edges.add(new Edge(targetId, trustDelta, condition));
                }

                String textKey = "survivor.player." + id;
                PLAYER_REACTIONS.put(id, new PlayerReaction(id, textKey, style, edges));
            }

            long elapsed = System.currentTimeMillis() - startTime;
            LOGGER.info("[SurvivorColonies] dialog_logic.bin geladen in {} ms: {} NPCs, {} Player, {} Roots, {} Styles.",
                    elapsed, NPC_REACTIONS.size(), PLAYER_REACTIONS.size(), ROOTS.size(), STYLE_TABLE.length);

        } catch (Exception e) {
            LOGGER.error("[SurvivorColonies] Kritischer Fehler beim Dekodieren der dialog_logic.bin: {}", e.getMessage(), e);
        }
    }

    private static Condition parseCondition(int condType, DataInputStream in) throws Exception {
        return switch (condType) {
            case COND_NONE -> new NoneCondition();
            case COND_TONE -> new ToneCondition(in.readUnsignedByte());
            case COND_BACKSTORY -> new BackstoryCondition(in.readUnsignedByte());
            case COND_MOTIVATION -> new MotivationCondition(in.readUnsignedByte());
            case COND_TRUST -> new TrustCondition(in.readShort(), in.readShort());
            case COND_SKILL -> new SkillCondition(in.readUnsignedByte(), in.readShort(), in.readShort());
            default -> throw new IllegalArgumentException("Unbekannter Condition-Typ im Stream: " + condType);
        };
    }

    public static List<Integer> getRoots() {
        return List.copyOf(ROOTS);
    }

    public static NpcReaction getNpcReaction(int id) {
        return NPC_REACTIONS.get(id);
    }

    public static PlayerReaction getPlayerReaction(int id) {
        return PLAYER_REACTIONS.get(id);
    }

    /**
     * Loest eine Spieler-Antwort gegen die Survivor-Eigenschaften auf.
     */
    public static EdgeResolution resolvePlayerReaction(SurvivorEntity survivor, int playerReactionId) {
        PlayerReaction playerReaction = PLAYER_REACTIONS.get(playerReactionId);
        if (playerReaction == null || playerReaction.edges().isEmpty()) {
            LOGGER.warn("[SurvivorColonies] Keine Spieler-Reaktion fuer ID #{} gefunden!", playerReactionId);
            return null;
        }

        List<Edge> matchingEdges = new ArrayList<>();
        Edge defaultEdge = null;

        for (Edge edge : playerReaction.edges()) {
            if (edge.condition() instanceof NoneCondition) {
                if (defaultEdge == null) {
                    defaultEdge = edge;
                }
                continue;
            }

            if (edge.condition().matches(survivor)) {
                matchingEdges.add(edge);
            }
        }

        Edge chosenEdge;
        if (!matchingEdges.isEmpty()) {
            if (matchingEdges.size() == 1) {
                chosenEdge = matchingEdges.get(0);
            } else {
                int selectedIdx = StableHash.variantPick(
                        matchingEdges.size(),
                        survivor.getUUID(),
                        "edge_eval:" + playerReactionId,
                        "selection"
                );
                chosenEdge = matchingEdges.get(selectedIdx);
            }
        } else if (defaultEdge != null) {
            chosenEdge = defaultEdge;
        } else {
            chosenEdge = playerReaction.edges().get(0);
        }

        NpcReaction targetNpc = NPC_REACTIONS.get(chosenEdge.targetId());
        if (targetNpc == null) {
            LOGGER.error("[SurvivorColonies] Kanten-Ziel NPC #{} nicht im Graph vorhanden!", chosenEdge.targetId());
            return null;
        }

        return new EdgeResolution(targetNpc, chosenEdge.trustDelta());
    }

    // --- CONDITION-RECORDS & MATCHING-LOGIK ---

    public sealed interface Condition permits NoneCondition, ToneCondition, BackstoryCondition,
            MotivationCondition, TrustCondition, SkillCondition {
        boolean matches(SurvivorEntity survivor);
    }

    public record NoneCondition() implements Condition {
        @Override
        public boolean matches(SurvivorEntity survivor) {
            return true;
        }
    }

    public record ToneCondition(int toneId) implements Condition {
        @Override
        public boolean matches(SurvivorEntity survivor) {
            if (toneId < 0 || toneId >= KNOWN_TONES.length) return false;
            String expected = KNOWN_TONES[toneId];
            return expected.equalsIgnoreCase(SurvivorPersonality.getTone(survivor.getUUID()));
        }
    }

    public record BackstoryCondition(int backstoryId) implements Condition {
        @Override
        public boolean matches(SurvivorEntity survivor) {
            if (backstoryId < 0 || backstoryId >= KNOWN_BACKSTORIES.length) return false;
            String expected = KNOWN_BACKSTORIES[backstoryId];
            return expected.equalsIgnoreCase(SurvivorPersonality.getBackstory(survivor.getUUID()));
        }
    }

    public record MotivationCondition(int motivationId) implements Condition {
        @Override
        public boolean matches(SurvivorEntity survivor) {
            if (motivationId < 0 || motivationId >= KNOWN_MOTIVATIONS.length) return false;
            String expected = KNOWN_MOTIVATIONS[motivationId];
            return expected.equalsIgnoreCase(SurvivorPersonality.getMotivation(survivor.getUUID()));
        }
    }

    public record TrustCondition(short min, short max) implements Condition {
        @Override
        public boolean matches(SurvivorEntity survivor) {
            int trust = survivor.getTrust();
            return trust >= min && trust <= max;
        }
    }

    public record SkillCondition(int skillId, short min, short max) implements Condition {
        @Override
        public boolean matches(SurvivorEntity survivor) {
            if (skillId < 0 || skillId >= KNOWN_SKILLS.length) return false;
            String skillName = KNOWN_SKILLS[skillId];
            int currentLevel = survivor.getSkills().getOrDefault(skillName, 1);
            return currentLevel >= min && currentLevel <= max;
        }
    }

    // --- STRUKTUR-RECORDS ---

    public record NpcReaction(int id, String textKey, List<Integer> options) {
        public String text() {
            return textKey;
        }
    }

    public record Edge(int targetId, int trustDelta, Condition condition) {}

    public record PlayerReaction(int id, String textKey, String style, List<Edge> edges) {
        public String text() {
            return textKey;
        }
    }

    public record EdgeResolution(NpcReaction npcReaction, int trustDelta) {}
}