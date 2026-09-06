package br.com.puccomp.api.identity.invitation;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("puccomp.onboarding")
public record OnboardingProperties(String acceptUrlBase, Duration invitationTtl) { }
