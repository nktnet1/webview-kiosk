package uk.nktnet.webviewkiosk.config.data

enum class ManagementOwner {
    None,
    DeviceOwner,
    ProfileOwner,
    Unknown,
}

enum class ManagementUserType {
    FullUser,
    ManagedProfile,
    Profile,
    Unknown,
}

data class ManagementCapabilities(
    val lockTaskPackages: Boolean = false,
    val lockTaskFeatures: Boolean = false,
    val lockDevice: Boolean = false,
    val transferOwnership: Boolean = false,
    val removeOwner: Boolean = false,
)
