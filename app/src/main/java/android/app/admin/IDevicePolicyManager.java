package android.app.admin;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

import androidx.annotation.Keep;

@Keep
public interface IDevicePolicyManager extends IInterface {
    void forceUpdateUserSetupComplete();

    void forceUpdateUserSetupComplete(int userId);

    @SuppressWarnings("unused")
    @Keep
    abstract class Stub extends Binder implements IDevicePolicyManager {
        public static IDevicePolicyManager asInterface(IBinder obj) {
            throw new UnsupportedOperationException();
        }
    }
}
