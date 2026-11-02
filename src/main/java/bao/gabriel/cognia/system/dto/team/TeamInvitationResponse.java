package bao.gabriel.cognia.system.dto.team;

public record TeamInvitationResponse(
        String id,
        String teamId,
        String teamName,
        String teamDescription,
        String teamLogoUrl,
        String inviterId,
        String inviterName,
        String inviteeId,
        String inviteeName,
        String status,
        String createTime
) {
}
