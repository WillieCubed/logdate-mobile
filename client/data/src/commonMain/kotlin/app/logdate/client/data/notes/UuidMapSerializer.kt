package app.logdate.client.data.notes

import app.logdate.util.UuidSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.uuid.Uuid


/**
 * A custom serializer for Map<Uuid, List<Uuid>> to handle UUID serialization.
 */
object UuidToUuidListMapSerializer : KSerializer<Map<Uuid, List<Uuid>>> {
    private val uuidSerializer = UuidSerializer
    private val listSerializer = ListSerializer(uuidSerializer)
    private val mapSerializer = MapSerializer(uuidSerializer, listSerializer)
    
    override val descriptor: SerialDescriptor = mapSerializer.descriptor
    
    override fun serialize(encoder: Encoder, value: Map<Uuid, List<Uuid>>) {
        mapSerializer.serialize(encoder, value)
    }
    
    override fun deserialize(decoder: Decoder): Map<Uuid, List<Uuid>> {
        return mapSerializer.deserialize(decoder)
    }
}