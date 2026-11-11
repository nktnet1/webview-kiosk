package uk.nktnet.webviewkiosk.activation;

import android.os.Bundle;

// Only activates this APK's admin receiver. No arbitrary command execution API.
interface IActivationService {
    Bundle activate(boolean profileOwner, int userId) = 1;
    void destroy() = 16777114;
}
