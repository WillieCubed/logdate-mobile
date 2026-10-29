"""Native backup helpers with explicit entry-point dependencies."""


def read_varint(h, data, offset):
    value = 0
    shift = 0
    while offset < len(data) and shift < 70:
        byte = data[offset]
        offset += 1
        value |= (byte & 0x7F) << shift
        if byte < 0x80:
            return value, offset
        shift += 7
    raise ValueError("invalid DataStore preferences: malformed varint")


def write_varint(h, value):
    encoded = bytearray()
    while value > 0x7F:
        encoded.append((value & 0x7F) | 0x80)
        value >>= 7
    encoded.append(value)
    return bytes(encoded)


def protobuf_fields(h, data):
    offset = 0
    while offset < len(data):
        start = offset
        tag, offset = h.read_varint(data, offset)
        number, wire_type = tag >> 3, tag & 7
        if number == 0:
            raise ValueError("invalid DataStore preferences: field number is zero")
        if wire_type == 0:
            payload, offset = h.read_varint(data, offset)
        elif wire_type == 1:
            payload = data[offset : offset + 8]
            offset += 8
        elif wire_type == 2:
            size, offset = h.read_varint(data, offset)
            payload = data[offset : offset + size]
            offset += size
        elif wire_type == 5:
            payload = data[offset : offset + 4]
            offset += 4
        else:
            raise ValueError("invalid DataStore preferences: unsupported wire type")
        if offset > len(data):
            raise ValueError("invalid DataStore preferences: field exceeds file size")
        yield number, wire_type, data[start:offset], payload


def length_delimited_field(h, number, value):
    return h.write_varint((number << 3) | 2) + h.write_varint(len(value)) + value


def validate_preference_value(h, data):
    fields = list(h.protobuf_fields(data))
    expected_wire_types = {1: 0, 2: 5, 3: 0, 4: 0, 5: 2, 6: 2, 7: 1, 8: 2}
    if len(fields) != 1:
        raise ValueError(
            "invalid DataStore preferences: value must have exactly one value field"
        )
    number, wire_type, _, payload = fields[0]
    if expected_wire_types.get(number) != wire_type:
        raise ValueError("invalid DataStore preferences: invalid preference value type")
    if number == 5:
        payload.decode("utf-8")
    if number == 8:
        for set_number, set_wire_type, _, set_payload in h.protobuf_fields(payload):
            if set_number != 1 or set_wire_type != 2:
                raise ValueError(
                    "invalid DataStore preferences: invalid string-set value"
                )
            set_payload.decode("utf-8")
    return number


def canonical_preferences(h, data, allowed_names=None, excluded_names=()):
    """Normalize DataStore preference ordering and omit its runtime search-index counter."""
    try:
        entries = {}
        for number, wire_type, encoded, entry in h.protobuf_fields(data):
            if number != 1 or wire_type != 2:
                raise ValueError("invalid DataStore preferences: unexpected map field")
            entry_fields = list(h.protobuf_fields(entry))
            name_fields = [
                payload
                for field_number, field_wire_type, _, payload in entry_fields
                if field_number == 1 and field_wire_type == 2
            ]
            values = [
                payload
                for field_number, field_wire_type, _, payload in entry_fields
                if field_number == 2 and field_wire_type == 2
            ]
            if len(entry_fields) != 2 or len(name_fields) != 1 or len(values) != 1:
                raise ValueError(
                    "invalid DataStore preferences: missing preference name"
                )
            name = name_fields[0].decode("utf-8")
            value_type = h.validate_preference_value(values[0])
            if name in entries:
                raise ValueError(
                    "invalid DataStore preferences: duplicate preference name"
                )
            if name == "android_platform_search_index_generation" and value_type != 4:
                raise ValueError(
                    "invalid DataStore preferences: runtime generation has invalid type"
                )
            if (
                name not in h.RUNTIME_ONLY_PREFERENCES
                and name not in excluded_names
                and (allowed_names is None or name in allowed_names)
            ):
                entries[name] = entry
        return b"".join(
            h.length_delimited_field(1, entry) for _, entry in sorted(entries.items())
        )
    except (IndexError, UnicodeDecodeError, ValueError) as error:
        if "invalid DataStore preferences" in str(error):
            raise
        raise ValueError(
            "invalid DataStore preferences: malformed preference map"
        ) from error


def preference_changes(h, before, after):
    """Identify unexpected preference writes without logging values or credentials."""

    def entries(data):
        result = {}
        for _, _, _, entry in h.protobuf_fields(h.canonical_preferences(data)):
            fields = list(h.protobuf_fields(entry))
            name = next(
                payload.decode()
                for number, wire, _, payload in fields
                if number == 1 and wire == 2
            )
            result[name] = entry
        return result

    original, prepared = entries(before), entries(after)
    return {
        "added": sorted(prepared.keys() - original.keys()),
        "removed": sorted(original.keys() - prepared.keys()),
        "changed": sorted(
            name
            for name in original.keys() & prepared.keys()
            if original[name] != prepared[name]
        ),
    }


def explicit_backup_privacy_defaults(h, data):
    """Expected first-backup normalization; existing values and other preferences stay intact."""
    normalized = h.canonical_preferences(data)
    names = {
        payload.decode()
        for _, _, _, entry in h.protobuf_fields(normalized)
        for number, wire, _, payload in h.protobuf_fields(entry)
        if number == 1 and wire == 2
    }
    defaults = {
        "is_onboarded": b"\x08\x00",
        "security_level": h.length_delimited_field(5, b"NONE"),
    }
    for name, value in defaults.items():
        if name not in names:
            entry = h.length_delimited_field(
                1, name.encode()
            ) + h.length_delimited_field(2, value)
            normalized += h.length_delimited_field(1, entry)
    return h.canonical_preferences(normalized)


FUNCTIONS = {
    "read_varint": read_varint,
    "write_varint": write_varint,
    "protobuf_fields": protobuf_fields,
    "length_delimited_field": length_delimited_field,
    "validate_preference_value": validate_preference_value,
    "canonical_preferences": canonical_preferences,
    "preference_changes": preference_changes,
    "explicit_backup_privacy_defaults": explicit_backup_privacy_defaults,
}
