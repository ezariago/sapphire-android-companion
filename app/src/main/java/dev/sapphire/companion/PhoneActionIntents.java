package dev.sapphire.companion;

import android.content.Intent;
import android.net.Uri;

final class PhoneActionIntents {
    static Intent review(PhoneActionPolicy.Draft draft) {
        if (draft == null) throw new IllegalArgumentException("Invalid draft");
        if ("sms".equals(draft.kind))
            return new Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", draft.number, null)).putExtra("sms_body", draft.body);
        return new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", draft.number, null));
    }
}
