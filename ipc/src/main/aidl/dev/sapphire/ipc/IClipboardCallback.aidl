package dev.sapphire.ipc;
oneway interface IClipboardCallback {
    void onClipboard(String text, long timestamp);
    void onStatus(String state);
}
