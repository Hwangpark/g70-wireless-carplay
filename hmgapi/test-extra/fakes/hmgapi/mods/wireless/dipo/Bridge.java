package hmgapi.mods.wireless.dipo;

/** host stand-in: the CarPlay session state G70Mute reads */
final class Bridge {
    static volatile Object sCp;
    static volatile boolean sCpClient, sCpWireless;
}
