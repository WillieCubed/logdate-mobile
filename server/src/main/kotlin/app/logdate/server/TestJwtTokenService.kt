package app.logdate.server

import app.logdate.server.auth.StubTokenService

/**
 * Simple test to verify JWT token generation and validation works correctly.
 */
fun main() {
    val tokenService = StubTokenService()
    
    println("Testing JWT Token Service")
    println("=========================")
    
    // Test access token
    val accountId = "test-account-123"
    val accessToken = tokenService.generateAccessToken(accountId)
    println("Generated access token: $accessToken")
    
    val validatedAccountId = tokenService.validateAccessToken(accessToken)
    println("Validated account ID: $validatedAccountId")
    println("Access token valid: ${validatedAccountId == accountId}")
    
    println()
    
    // Test refresh token
    val refreshToken = tokenService.generateRefreshToken(accountId)
    println("Generated refresh token: $refreshToken")
    
    val validatedRefreshAccountId = tokenService.validateRefreshToken(refreshToken)
    println("Validated refresh account ID: $validatedRefreshAccountId")
    println("Refresh token valid: ${validatedRefreshAccountId == accountId}")
    
    println()
    
    // Test session token
    val sessionId = "session-456"
    val sessionToken = tokenService.generateSessionToken(sessionId)
    println("Generated session token: $sessionToken")
    
    val validatedSessionId = tokenService.validateSessionToken(sessionToken)
    println("Validated session ID: $validatedSessionId")
    println("Session token valid: ${validatedSessionId == sessionId}")
    
    println()
    
    // Test invalid token
    val invalidToken = "invalid.token.here"
    val invalidResult = tokenService.validateAccessToken(invalidToken)
    println("Invalid token result: $invalidResult")
    println("Invalid token correctly rejected: ${invalidResult == null}")
}