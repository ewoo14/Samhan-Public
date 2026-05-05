package com.samhanair.logis.partnerauth.dto;

/** PATCH /api/v1/auth/partner-tutorial 응답. */
public record TutorialUpdateResponse(
        String bizNo,
        boolean tutorialPcDone,
        boolean tutorialMobileDone
) {}
