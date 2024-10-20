package app.logdate.core.data.user.devices

enum class DeviceType {
    /**
     * A mobile phone.
     */
    MOBILE,

    /**
     * A tablet computer.
     */
    TABLET,

    /**
     * A smartwatch.
     */
    WATCH,

    /**
     * Any computer that is not a mobile phone or tablet.
     *
     * This includes laptops and desktop computers.
     */
    DESKTOP,

    /**
     * Unknown device type.
     */
    OTHER
}