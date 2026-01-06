/*
 * Copyright (c) 2025 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.meshtastic.core.model

import android.os.Parcel
import android.os.Parcelable

// MyNodeInfo sent via special protobuf from radio
// Using explicit Parcelable implementation for Android 13+ compatibility with ATAK plugin ClassLoader
data class MyNodeInfo(
    val myNodeNum: Int,
    val hasGPS: Boolean,
    val model: String?,
    val firmwareVersion: String?,
    val couldUpdate: Boolean, // this application contains a software load we _could_ install if you want
    val shouldUpdate: Boolean, // this device has old firmware
    val currentPacketId: Long,
    val messageTimeoutMsec: Int,
    val minAppVersion: Int,
    val maxChannels: Int,
    val hasWifi: Boolean,
    val channelUtilization: Float,
    val airUtilTx: Float,
    val deviceId: String?,
) : Parcelable {
    /** A human readable description of the software/hardware version */
    val firmwareString: String
        get() = "$model $firmwareVersion"

    constructor(parcel: Parcel) : this(
        parcel.readInt(),
        parcel.readInt() == 1,
        parcel.readString(),
        parcel.readString(),
        parcel.readInt() == 1,
        parcel.readInt() == 1,
        parcel.readLong(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt() == 1,
        parcel.readFloat(),
        parcel.readFloat(),
        parcel.readString(),
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(myNodeNum)
        parcel.writeInt(if (hasGPS) 1 else 0)
        parcel.writeString(model)
        parcel.writeString(firmwareVersion)
        parcel.writeInt(if (couldUpdate) 1 else 0)
        parcel.writeInt(if (shouldUpdate) 1 else 0)
        parcel.writeLong(currentPacketId)
        parcel.writeInt(messageTimeoutMsec)
        parcel.writeInt(minAppVersion)
        parcel.writeInt(maxChannels)
        parcel.writeInt(if (hasWifi) 1 else 0)
        parcel.writeFloat(channelUtilization)
        parcel.writeFloat(airUtilTx)
        parcel.writeString(deviceId)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<MyNodeInfo> {
        override fun createFromParcel(parcel: Parcel): MyNodeInfo = MyNodeInfo(parcel)
        override fun newArray(size: Int): Array<MyNodeInfo?> = arrayOfNulls(size)
    }
}