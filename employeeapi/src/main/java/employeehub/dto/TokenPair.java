package employeehub.dto;

/**
 * A freshly minted access token paired with its (rotated) refresh token.
 * Returned by login and by the refresh endpoint.
 */
public record TokenPair(String accessToken, String refreshToken) {
}
