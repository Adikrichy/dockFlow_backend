package org.aldousdev.dockflowbackend.auth.dto.request;

import lombok.Getter;
import lombok.Setter;
import org.aldousdev.dockflowbackend.auth.enums.InviteChannel;

@Getter
@Setter
public class InviteUserRequest {
    private String email;
    private InviteChannel channel;
    private Long roleId;

}
