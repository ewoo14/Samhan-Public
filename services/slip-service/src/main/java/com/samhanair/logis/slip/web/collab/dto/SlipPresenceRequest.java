package com.samhanair.logis.slip.web.collab.dto;

/** 전표 협업 presence join/leave 요청. userId/displayName 은 인증 헤더가 있으면 헤더가 우선한다. */
public record SlipPresenceRequest(
        String userId,
        String displayName) {
}
