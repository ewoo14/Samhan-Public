package com.samhanair.logis.slip.web.collab.dto;

/** 전표 협업 presence join/leave 요청. sessionId 는 클라이언트 mount 단위 opaque 식별자다. */
public record SlipPresenceRequest(
        String sessionId,
        String displayName) {
}
