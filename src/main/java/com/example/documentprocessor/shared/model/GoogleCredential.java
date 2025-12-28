package com.example.documentprocessor.shared.model;

/**
 * Represents Google API credentials with both ID and key.
 * Supports the n8n-style credential ID concept along with actual API keys.
 */
public record GoogleCredential(String credentialId, String apiKey) {

    /**
     * Creates a GoogleCredential with both ID and key.
     *
     * @param credentialId The credential identifier (from n8n-style configuration)
     * @param apiKey The actual API key or access token
     */
    public GoogleCredential {
        if (credentialId == null || credentialId.trim().isEmpty()) {
            throw new IllegalArgumentException("Credential ID cannot be null or empty");
        }
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API key cannot be null or empty");
        }
    }

    /**
     * Gets the authorization header value for HTTP requests.
     *
     * @return The Bearer token header value
     */
    public String getAuthorizationHeader() {
        return "Bearer " + apiKey;
    }
}