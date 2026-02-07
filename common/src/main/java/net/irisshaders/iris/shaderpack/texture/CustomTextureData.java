package net.irisshaders.iris.shaderpack.texture;

import com.mojang.blaze3d.opengl.GlConst;
import com.mojang.blaze3d.platform.NativeImage;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.texture.InternalTextureFormat;
import net.irisshaders.iris.gl.texture.PixelFormat;
import net.irisshaders.iris.gl.texture.PixelType;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public abstract class CustomTextureData {
	private CustomTextureData() {

	}

	public static final class PngData extends CustomTextureData {
		private final TextureFilteringData filteringData;
		private final ImageData imageData;

		private PngData(TextureFilteringData filteringData, ImageData imageData) throws IOException {
			this.filteringData = filteringData;
			this.imageData = imageData;
		}

		public TextureFilteringData getFilteringData() {
			return filteringData;
		}

		public ByteBuffer getContent() {
			return imageData.content.asReadOnlyBuffer();
		}

		public int getWidth() {
			return imageData.width;
		}

		public int getHeight() {
			return imageData.height;
		}

		public int getInternalFormat() {
			return InternalTextureFormat.RGBA8.getGlFormat();
		}

		public int getPixelFormat() {
			return GlConst.toGl(imageData.format);
		}

		public int getPixelType() {
			return PixelType.UNSIGNED_BYTE.getGlFormat();
		}

		public int getAlignment() {
			return imageData.format.components();
		}

		private record ImageData(ByteBuffer content, int width, int height, NativeImage.Format format) {}

		private record HashKey(byte[] hash) {
			@Override
			public boolean equals(Object o) {
				if (o == null || getClass() != o.getClass()) return false;

				HashKey hashKey = (HashKey) o;
				return Arrays.equals(hash, hashKey.hash);
			}

			@Override
			public int hashCode() {
				return Arrays.hashCode(hash);
			}
		}

		private static final ConcurrentHashMap<HashKey, SoftReference<ImageData>> imageCache = new ConcurrentHashMap<>();

		public static PngData getOrCreate(TextureFilteringData filteringData, Path path) throws IOException {
			ImageData imageData = null;
			try (var fileChannel = FileChannel.open(path, StandardOpenOption.READ)) {
				imageData = getOrCreate(fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size()));
			} catch (UnsupportedOperationException e) {
				try (var channel = Files.newByteChannel(path, StandardOpenOption.READ)) {
					var buffer = MemoryUtil.memAlloc((int) channel.size());
					try {
						channel.read(buffer);
						buffer.flip();
						imageData = getOrCreate(buffer);
					} finally {
						MemoryUtil.memFree(buffer);
					}
				}
			}
			if (imageData == null) {
				throw new IOException("Failed to load image from " + path);
			}
			return new PngData(filteringData, imageData);
		}

		private static ImageData getOrCreate(ByteBuffer data) {
			try {
				MessageDigest md5 = MessageDigest.getInstance("SHA-256");
				md5.update(data.asReadOnlyBuffer());
				var hashKey = new HashKey(md5.digest());
				var imageData = new AtomicReference<ImageData>();
				imageCache.compute(hashKey, (k, c) -> {
					if (c != null) {
						ImageData prevImageData = c.get();
						imageData.set(prevImageData);
						if (prevImageData != null) {
							return c;
						}
					}
					try (var nativeImage = NativeImage.read(data)) {
						var size = nativeImage.getWidth() * nativeImage.getHeight() * nativeImage.format().components();
						var src = MemoryUtil.memByteBuffer(nativeImage.getPointer(), size);
						var dst = ByteBuffer.allocateDirect(size);
						dst.put(src);
						dst.flip();
						ImageData newImageData = new ImageData(
							dst,
							nativeImage.getWidth(),
							nativeImage.getHeight(),
							nativeImage.format()
						);
						imageData.set(newImageData);
						return new SoftReference<>(newImageData);
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				});
				return imageData.get();
			} catch (NoSuchAlgorithmException e) {
				throw new RuntimeException(e);
			}
		}
	}

	public static final class LightmapMarker extends CustomTextureData {
		@Override
		public boolean equals(Object obj) {
			return obj.getClass() == this.getClass();
		}

		@Override
		public int hashCode() {
			return 33;
		}
	}

	public static final class ResourceData extends CustomTextureData {
		private final String namespace;
		private final String location;

		public ResourceData(String namespace, String location) {
			this.namespace = namespace;
			this.location = location;
		}

		/**
		 * @return The namespace of the texture. The caller is responsible for checking whether this is actually
		 * a valid namespace.
		 */
		public String getNamespace() {
			return namespace;
		}

		/**
		 * @return The path / location of the texture. The caller is responsible for checking whether this is actually
		 * a valid path.
		 */
		public String getLocation() {
			return location;
		}
	}

	public abstract static class RawData extends CustomTextureData {
		private final ByteBuffer content;
		private final InternalTextureFormat internalFormat;
		private final PixelFormat pixelFormat;
		private final PixelType pixelType;
		private final TextureFilteringData filteringData;

		private RawData(
			ByteBuffer content, TextureFilteringData filteringData, InternalTextureFormat internalFormat,
			PixelFormat pixelFormat, PixelType pixelType
		) {
			this.content = content;
			this.filteringData = filteringData;
			this.internalFormat = internalFormat;
			this.pixelFormat = pixelFormat;
			this.pixelType = pixelType;
		}

		public final ByteBuffer getContent() {
			return content.asReadOnlyBuffer();
		}

		public TextureFilteringData getFilteringData() {
			return filteringData;
		}

		public final InternalTextureFormat getInternalFormat() {
			return internalFormat;
		}

		public final PixelFormat getPixelFormat() {
			return pixelFormat;
		}

		public final PixelType getPixelType() {
			return pixelType;
		}
	}

	public static final class RawData1D extends RawData {
		private final int sizeX;

		public RawData1D(
			ByteBuffer content, TextureFilteringData filteringData, InternalTextureFormat internalFormat,
			PixelFormat pixelFormat, PixelType pixelType, int sizeX
		) {
			super(content, filteringData, internalFormat, pixelFormat, pixelType);
			int expectedSize = sizeX * pixelFormat.getComponentCount() * pixelType.getByteSize();

			if (content.remaining() < expectedSize) {
				throw new IllegalStateException("1D Custom texture was " + content.remaining() + " bytes; expected " + expectedSize);
			} else if (content.remaining() > expectedSize) {
				Iris.logger.warn("1D Custom texture was " + content.remaining() + " bytes; expected " + expectedSize + ". This is allowed, but you probably don't want this.");
			}

			this.sizeX = sizeX;
		}

		public int getSizeX() {
			return sizeX;
		}
	}

	public static class RawData2D extends RawData {
		final int sizeX;
		final int sizeY;

		public RawData2D(
			ByteBuffer content, TextureFilteringData filteringData, InternalTextureFormat internalFormat,
			PixelFormat pixelFormat, PixelType pixelType, int sizeX, int sizeY
		) {
			super(content, filteringData, internalFormat, pixelFormat, pixelType);

			int expectedSize = sizeX * sizeY * pixelFormat.getComponentCount() * pixelType.getByteSize();

			if (content.remaining() < expectedSize) {
				throw new IllegalStateException("2D Custom texture was " + content.remaining() + " bytes; expected " + expectedSize);
			} else if (content.remaining() > expectedSize) {
				Iris.logger.warn("2D Custom texture was " + content.remaining() + " bytes; expected " + expectedSize + ". This is allowed, but you probably don't want this.");
			}

			this.sizeX = sizeX;
			this.sizeY = sizeY;
		}

		public int getSizeX() {
			return sizeX;
		}

		public int getSizeY() {
			return sizeY;
		}
	}

	public static final class RawData3D extends RawData {
		final int sizeX;
		final int sizeY;
		final int sizeZ;

		public RawData3D(
			ByteBuffer content, TextureFilteringData filteringData, InternalTextureFormat internalFormat,
			PixelFormat pixelFormat, PixelType pixelType, int sizeX, int sizeY, int sizeZ
		) {
			super(content, filteringData, internalFormat, pixelFormat, pixelType);

			int expectedSize = sizeX * sizeY * sizeZ * pixelFormat.getComponentCount() * pixelType.getByteSize();

			if (content.remaining() < expectedSize) {
				throw new IllegalStateException("3D Custom texture was " + content.remaining() + " bytes; expected " + expectedSize);
			} else if (content.remaining() > expectedSize) {
				Iris.logger.warn("3D Custom texture was " + content.remaining() + " bytes; expected " + expectedSize + ". This is allowed, but you probably don't want this.");
			}

			this.sizeX = sizeX;
			this.sizeY = sizeY;
			this.sizeZ = sizeZ;
		}

		public int getSizeX() {
			return sizeX;
		}

		public int getSizeY() {
			return sizeY;
		}

		public int getSizeZ() {
			return sizeZ;
		}
	}

	public static class RawDataRect extends RawData2D {
		public RawDataRect(
			ByteBuffer content,
			TextureFilteringData filteringData,
			InternalTextureFormat internalFormat,
			PixelFormat pixelFormat,
			PixelType pixelType,
			int sizeX,
			int sizeY
		) {
			super(content, filteringData, internalFormat, pixelFormat, pixelType, sizeX, sizeY);
		}
	}
}
