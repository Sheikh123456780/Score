package top.niunaijun.blackbox.entity;

import android.content.ComponentName;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by Milk on 4/7/21.
 * Updated for Android 16 compatibility
 * Added session field for new ServiceConnection API
 */
public class UnbindRecord implements Parcelable {
    private int mBindCount;
    private int mStartId;
    private ComponentName mComponentName;
    
    // Android 16+ session parameter
    // Not parcelled because session is not Parcelable in all cases
    private transient Object mSession;

    public int getStartId() {
        return mStartId;
    }

    public void setStartId(int startId) {
        mStartId = startId;
    }

    public int getBindCount() {
        return mBindCount;
    }

    public void setBindCount(int bindCount) {
        mBindCount = bindCount;
    }

    public ComponentName getComponentName() {
        return mComponentName;
    }

    public void setComponentName(ComponentName componentName) {
        mComponentName = componentName;
    }

    /**
     * Get session for Android 16+
     */
    public Object getSession() {
        return mSession;
    }

    /**
     * Set session for Android 16+
     */
    public void setSession(Object session) {
        this.mSession = session;
    }

    public UnbindRecord() {
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.mBindCount);
        dest.writeInt(this.mStartId);
        dest.writeParcelable(this.mComponentName, flags);
        // Session is transient, not written to parcel
    }

    protected UnbindRecord(Parcel in) {
        this.mBindCount = in.readInt();
        this.mStartId = in.readInt();
        this.mComponentName = in.readParcelable(ComponentName.class.getClassLoader());
        // Session is transient, not read from parcel
    }

    public static final Creator<UnbindRecord> CREATOR = new Creator<UnbindRecord>() {
        @Override
        public UnbindRecord createFromParcel(Parcel source) {
            return new UnbindRecord(source);
        }

        @Override
        public UnbindRecord[] newArray(int size) {
            return new UnbindRecord[size];
        }
    };
}
