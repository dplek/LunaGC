package emu.grasscutter.server.packet.recv;

import java.util.List;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player.SceneLoadState;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.EnterSceneDoneReqOuterClass.EnterSceneDoneReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.net.proto.ActivityInfoOuterClass.ActivityInfo;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.proto.ChangeHpDebtsReasonOuterClass;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass;
import emu.grasscutter.server.packet.send.PacketEntityFightPropChangeReasonNotify;
import emu.grasscutter.server.packet.send.*;

@Opcodes(PacketOpcodes.EnterSceneDoneReq)
public class HandlerEnterSceneDoneReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        EnterSceneDoneReq req = EnterSceneDoneReq.parseFrom(payload);
        
        var player = session.getPlayer();

        // Finished loading
        player.setSceneLoadState(SceneLoadState.LOADED);

        // Done

        session.send(new PacketPlayerTimeNotify(player)); // Probably not the right place

        // Spawn player in world
        player.getScene().spawnPlayer(player);

        // Spawn other entites already in world
        player.getScene().showOtherEntities(player);

        // Locations
        session.send(new PacketWorldPlayerLocationNotify(player.getWorld()));
        session.send(new PacketScenePlayerLocationNotify(player.getScene()));
        session.send(new PacketWorldPlayerRTTNotify(player.getWorld()));
        var avatarEntity = player.getTeamManager().getCurrentAvatarEntity();
        float currentHpDebts = avatarEntity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        if (currentHpDebts > 0.0f) {
            avatarEntity.getWorld()
                    .broadcastPacket(new PacketEntityFightPropChangeReasonNotify(avatarEntity,
                            FightProperty.FIGHT_PROP_CUR_HP_DEBTS, currentHpDebts,
                            PropChangeReasonOuterClass.PropChangeReason.PROP_CHANGE_REASON_NONE,
                            ChangeHpDebtsReasonOuterClass.ChangeHpDebtsReason.CHANGE_HP_DEBTS_NONE));
        }
        List<Integer> defaultActivityIds = Grasscutter.getConfig().server.game.gameOptions.defaultActivityIds;

        for (int activityId : defaultActivityIds) {
            int scheduleId = activityId * 1000 + 1;

            ActivityInfo info = ActivityInfo.newBuilder()
                    .setBeginTime((int) (System.currentTimeMillis() / 1000 - 1000)) // 开始时间（稍微过去）
                    .setEndTime((int) (System.currentTimeMillis() / 1000 + 10000000)) // 结束时间（很久以后）
                    .setActivityId(activityId)
                    .setScheduleId(scheduleId)
                    .setIsFinished(false)
                    .build();

            session.send(new PacketActivityInfoNotify(info));
        }

        // spawn NPC
        player.getScene().loadNpcForPlayerEnter(player);

        // notify client to load the npc for quest
        var questGroupSuites = player.getQuestManager().getSceneGroupSuite(player.getSceneId());

        player.getScene().loadGroupForQuest(questGroupSuites);
        Grasscutter.getLogger()
                .trace("Loaded Scene {} Quest(s) Groupsuite(s): {}", player.getSceneId(), questGroupSuites);
        session.send(new PacketGroupSuiteNotify(questGroupSuites));

        // Reset timer for sending player locations
        player.resetSendPlayerLocTime();

        // Rsp
        session.send(new PacketEnterSceneDoneRsp(player));
    }
}