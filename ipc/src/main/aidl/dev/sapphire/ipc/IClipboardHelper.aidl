package dev.sapphire.ipc;
import dev.sapphire.ipc.IClipboardCallback;
interface IClipboardHelper {
    void start(IClipboardCallback callback);
    void stop(IClipboardCallback callback);
}
