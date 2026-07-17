package net.momirealms.craftengine.core.util;

import net.momirealms.sparrow.nbt.CompoundTag;
import net.momirealms.sparrow.nbt.NBT;

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class NBTUtils {

    private NBTUtils() {
    }

    public static void writeCompressed(CompoundTag nbt, OutputStream stream) throws IOException {
        try (DataOutputStream dataoutputstream = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(stream)))) {
            NBT.writeCompound(nbt, dataoutputstream, true);
        }
    }

    public static CompoundTag readCompressed(InputStream stream) throws IOException {
        try (DataInputStream datainputstream = new DataInputStream(new BufferedInputStream(new GZIPInputStream(stream)))) {
            return NBT.readCompound(datainputstream, true);
        }
    }
}
