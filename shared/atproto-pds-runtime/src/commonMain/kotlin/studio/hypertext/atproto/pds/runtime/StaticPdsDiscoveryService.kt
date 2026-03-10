package studio.hypertext.atproto.pds.runtime

import studio.hypertext.atproto.pds.AuthorizationServerMetadata
import studio.hypertext.atproto.pds.DescribeServerResponse
import studio.hypertext.atproto.pds.PdsDiscoveryService
import studio.hypertext.atproto.pds.ProtectedResourceMetadata

/**
 * Static discovery service implementation for standalone PDS runtimes and servers.
 */
public class StaticPdsDiscoveryService(
    private val authorizationServerMetadata: AuthorizationServerMetadata,
    private val protectedResourceMetadata: ProtectedResourceMetadata,
    private val describeServerResponse: DescribeServerResponse,
) : PdsDiscoveryService {
    override fun authorizationServerMetadata(): AuthorizationServerMetadata = authorizationServerMetadata

    override fun protectedResourceMetadata(): ProtectedResourceMetadata = protectedResourceMetadata

    override fun describeServer(): DescribeServerResponse = describeServerResponse
}
