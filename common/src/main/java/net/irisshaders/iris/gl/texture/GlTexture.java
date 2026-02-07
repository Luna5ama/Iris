package net.irisshaders.iris.gl.texture;

import com.mojang.blaze3d.opengl.GlStateManager;
import net.irisshaders.iris.gl.GlResource;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.sampler.GlSampler;
import net.irisshaders.iris.shaderpack.texture.CustomTextureData;
import net.irisshaders.iris.shaderpack.texture.TextureFilteringData;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.IntSupplier;

import static org.lwjgl.opengl.GL11C.GL_UNPACK_ALIGNMENT;
import static org.lwjgl.opengl.GL11C.GL_UNPACK_ROW_LENGTH;
import static org.lwjgl.opengl.GL11C.GL_UNPACK_SKIP_PIXELS;
import static org.lwjgl.opengl.GL11C.GL_UNPACK_SKIP_ROWS;

public class GlTexture extends GlResource implements TextureAccess {
	private final TextureType target;
	private final boolean shouldBlur;
	private final boolean shouldClamp;

	private GlTexture(int id, TextureType target, boolean shouldBlur, boolean shouldClamp) {
		super(id);
		this.target = target;
		this.shouldBlur = shouldBlur;
		this.shouldClamp = shouldClamp;
	}

	public static GlTexture create(
		TextureType target,
		int sizeX,
		int sizeY,
		int sizeZ,
		int internalFormat,
		int format,
		int pixelType,
		ByteBuffer buffer,
		int alignment,
		TextureFilteringData filteringData
	) {
		int id = GlStateManager._genTexture();
		IrisRenderSystem.bindTextureForSetup(target.getGlType(), id);

		TextureUploadHelper.resetTextureUploadState();
		if (alignment != -1) {
			GlStateManager._pixelStore(GL_UNPACK_ROW_LENGTH, sizeX);
			GlStateManager._pixelStore(GL_UNPACK_SKIP_PIXELS, 0);
			GlStateManager._pixelStore(GL_UNPACK_SKIP_ROWS, 0);
			GlStateManager._pixelStore(GL_UNPACK_ALIGNMENT, alignment);
		}

		target.apply(id, sizeX, sizeY, sizeZ, internalFormat, format, pixelType, buffer.asReadOnlyBuffer().order(ByteOrder.nativeOrder()));

		IrisRenderSystem.texParameteri(id, target.getGlType(), GL11C.GL_TEXTURE_MIN_FILTER, filteringData.shouldBlur() ? GL11C.GL_LINEAR : GL11C.GL_NEAREST);
		IrisRenderSystem.texParameteri(id, target.getGlType(), GL11C.GL_TEXTURE_MAG_FILTER, filteringData.shouldBlur() ? GL11C.GL_LINEAR : GL11C.GL_NEAREST);
		IrisRenderSystem.texParameteri(id, target.getGlType(), GL11C.GL_TEXTURE_WRAP_S, filteringData.shouldClamp() ? GL13C.GL_CLAMP_TO_EDGE : GL13C.GL_REPEAT);
		boolean shouldBlur = filteringData.shouldBlur();
		boolean shouldClamp = filteringData.shouldClamp();

		if (sizeY > 0) {
			IrisRenderSystem.texParameteri(id, target.getGlType(), GL11C.GL_TEXTURE_WRAP_T, filteringData.shouldClamp() ? GL13C.GL_CLAMP_TO_EDGE : GL13C.GL_REPEAT);
		}

		if (sizeZ > 0) {
			IrisRenderSystem.texParameteri(id, target.getGlType(), GL30C.GL_TEXTURE_WRAP_R, filteringData.shouldClamp() ? GL13C.GL_CLAMP_TO_EDGE : GL13C.GL_REPEAT);
		}

		IrisRenderSystem.texParameteri(id, target.getGlType(), GL20C.GL_TEXTURE_MAX_LEVEL, 0);
		IrisRenderSystem.texParameteri(id, target.getGlType(), GL20C.GL_TEXTURE_MIN_LOD, 0);
		IrisRenderSystem.texParameteri(id, target.getGlType(), GL20C.GL_TEXTURE_MAX_LOD, 0);
		IrisRenderSystem.texParameterf(id, target.getGlType(), GL20C.GL_TEXTURE_LOD_BIAS, 0.0F);

		IrisRenderSystem.bindTextureForSetup(target.getGlType(), 0);

		return new GlTexture(id, target, shouldBlur, shouldClamp);
	}

	public static GlTexture create(
		TextureType target,
		int sizeX,
		int sizeY,
		int sizeZ,
		int internalFormat,
		int format,
		int pixelType,
		ByteBuffer buffer,
		TextureFilteringData filteringData
	) {
		return create(target, sizeX, sizeY, sizeZ, internalFormat, format, pixelType, buffer, -1, filteringData);
	}

	public static GlTexture create(TextureType target, CustomTextureData.PngData pngData) {
		return create(
			target,
			pngData.getWidth(),
			pngData.getHeight(),
			1,
			pngData.getInternalFormat(),
			pngData.getPixelFormat(),
			pngData.getPixelType(),
			pngData.getContent(),
			pngData.getAlignment(),
			pngData.getFilteringData()
		);
	}

	public TextureType getTarget() {
		return target;
	}

	public void bind(int unit) {
		IrisRenderSystem.bindTextureToUnit(target.getGlType(), unit, getGlId());
	}

	@Override
	public TextureType getType() {
		return target;
	}

	@Override
	public IntSupplier getTextureId() {
		return this::getGlId;
	}

	@Override
	public GlSampler getSampling() {
		if (shouldClamp) {
			if (shouldBlur) {
				return GlSampler.LINEAR;
			} else {
				return GlSampler.NEAREST;
			}
		} else {
			if (shouldBlur) {
				return GlSampler.LINEAR_REPEAT;
			} else {
				return GlSampler.NEAREST_REPEAT;
			}
		}
	}

	@Override
	protected void destroyInternal() {
		GlStateManager._deleteTexture(getGlId());
	}
}
