package android.opengl

import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Headless GLES20: real GL constants, success-value no-ops. All methods @JvmStatic (matches android.jar). */
object GLES20 {
    const val GL_ACTIVE_ATTRIBUTES = 0x8B89
    const val GL_ACTIVE_TEXTURE = 0x84E0
    const val GL_ACTIVE_UNIFORMS = 0x8B86
    const val GL_ALIASED_LINE_WIDTH_RANGE = 0x846E
    const val GL_ALIASED_POINT_SIZE_RANGE = 0x846D
    const val GL_ALPHA = 0x1906
    const val GL_ALPHA_BITS = 0xD55
    const val GL_ALWAYS = 0x207
    const val GL_ARRAY_BUFFER = 0x8892
    const val GL_ARRAY_BUFFER_BINDING = 0x8894
    const val GL_ATTACHED_SHADERS = 0x8B85
    const val GL_BACK = 0x405
    const val GL_BLEND = 0xBE2
    const val GL_BLEND_COLOR = 0x8005
    const val GL_BLEND_DST_ALPHA = 0x80CA
    const val GL_BLEND_DST_RGB = 0x80C8
    const val GL_BLEND_EQUATION = 0x8009
    const val GL_BLEND_EQUATION_ALPHA = 0x883D
    const val GL_BLEND_EQUATION_RGB = 0x8009
    const val GL_BLEND_SRC_ALPHA = 0x80CB
    const val GL_BLEND_SRC_RGB = 0x80C9
    const val GL_BLUE_BITS = 0xD54
    const val GL_BOOL = 0x8B56
    const val GL_BOOL_VEC2 = 0x8B57
    const val GL_BOOL_VEC3 = 0x8B58
    const val GL_BOOL_VEC4 = 0x8B59
    const val GL_BUFFER_SIZE = 0x8764
    const val GL_BUFFER_USAGE = 0x8765
    const val GL_BYTE = 0x1400
    const val GL_CCW = 0x901
    const val GL_CLAMP_TO_EDGE = 0x812F
    const val GL_COLOR_ATTACHMENT0 = 0x8CE0
    const val GL_COLOR_BUFFER_BIT = 0x4000
    const val GL_COLOR_CLEAR_VALUE = 0xC22
    const val GL_COLOR_WRITEMASK = 0xC23
    const val GL_COMPILE_STATUS = 0x8B81
    const val GL_COMPRESSED_TEXTURE_FORMATS = 0x86A3
    const val GL_CONSTANT_ALPHA = 0x8003
    const val GL_CONSTANT_COLOR = 0x8001
    const val GL_CULL_FACE = 0xB44
    const val GL_CULL_FACE_MODE = 0xB45
    const val GL_CURRENT_PROGRAM = 0x8B8D
    const val GL_CURRENT_VERTEX_ATTRIB = 0x8626
    const val GL_CW = 0x900
    const val GL_DECR = 0x1E03
    const val GL_DECR_WRAP = 0x8508
    const val GL_DEPTH_ATTACHMENT = 0x8D00
    const val GL_DEPTH_BITS = 0xD56
    const val GL_DEPTH_BUFFER_BIT = 0x100
    const val GL_DEPTH_CLEAR_VALUE = 0xB73
    const val GL_DEPTH_COMPONENT = 0x1902
    const val GL_DEPTH_COMPONENT16 = 0x81A5
    const val GL_DEPTH_FUNC = 0xB74
    const val GL_DEPTH_RANGE = 0xB70
    const val GL_DEPTH_TEST = 0xB71
    const val GL_DEPTH_WRITEMASK = 0xB72
    const val GL_DITHER = 0xBD0
    const val GL_DONT_CARE = 0x1100
    const val GL_DST_ALPHA = 0x304
    const val GL_DST_COLOR = 0x306
    const val GL_DYNAMIC_DRAW = 0x88E8
    const val GL_ELEMENT_ARRAY_BUFFER = 0x8893
    const val GL_ELEMENT_ARRAY_BUFFER_BINDING = 0x8895
    const val GL_EQUAL = 0x202
    const val GL_EXTENSIONS = 0x1F03
    const val GL_FALSE = 0
    const val GL_FASTEST = 0x1101
    const val GL_FIXED = 0x140C
    const val GL_FLOAT = 0x1406
    const val GL_FLOAT_MAT2 = 0x8B5A
    const val GL_FLOAT_MAT3 = 0x8B5B
    const val GL_FLOAT_MAT4 = 0x8B5C
    const val GL_FLOAT_VEC2 = 0x8B50
    const val GL_FLOAT_VEC3 = 0x8B51
    const val GL_FLOAT_VEC4 = 0x8B52
    const val GL_FRAGMENT_SHADER = 0x8B30
    const val GL_FRAMEBUFFER = 0x8D40
    const val GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME = 0x8CD1
    const val GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE = 0x8CD0
    const val GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_CUBE_MAP_FACE = 0x8CD3
    const val GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL = 0x8CD2
    const val GL_FRAMEBUFFER_BINDING = 0x8CA6
    const val GL_FRAMEBUFFER_COMPLETE = 0x8CD5
    const val GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT = 0x8CD6
    const val GL_FRAMEBUFFER_INCOMPLETE_DIMENSIONS = 0x8CD9
    const val GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT = 0x8CD7
    const val GL_FRAMEBUFFER_UNSUPPORTED = 0x8CDD
    const val GL_FRONT = 0x404
    const val GL_FRONT_AND_BACK = 0x408
    const val GL_FRONT_FACE = 0xB46
    const val GL_FUNC_ADD = 0x8006
    const val GL_FUNC_REVERSE_SUBTRACT = 0x800B
    const val GL_FUNC_SUBTRACT = 0x800A
    const val GL_GENERATE_MIPMAP_HINT = 0x8192
    const val GL_GEQUAL = 0x206
    const val GL_GREATER = 0x204
    const val GL_GREEN_BITS = 0xD53
    const val GL_HIGH_FLOAT = 0x8DF5
    const val GL_HIGH_INT = 0x8DFB
    const val GL_INCR = 0x1E02
    const val GL_INCR_WRAP = 0x8507
    const val GL_INFO_LOG_LENGTH = 0x8B84
    const val GL_INT = 0x1404
    const val GL_INT_VEC2 = 0x8B53
    const val GL_INT_VEC3 = 0x8B54
    const val GL_INT_VEC4 = 0x8B55
    const val GL_INVALID_ENUM = 0x500
    const val GL_INVALID_FRAMEBUFFER_OPERATION = 0x506
    const val GL_INVALID_OPERATION = 0x502
    const val GL_INVALID_VALUE = 0x501
    const val GL_INVERT = 0x150A
    const val GL_KEEP = 0x1E00
    const val GL_LEQUAL = 0x203
    const val GL_LESS = 0x201
    const val GL_LINEAR = 0x2601
    const val GL_LINEAR_MIPMAP_LINEAR = 0x2703
    const val GL_LINEAR_MIPMAP_NEAREST = 0x2701
    const val GL_LINES = 0x1
    const val GL_LINE_LOOP = 0x2
    const val GL_LINE_STRIP = 0x3
    const val GL_LINE_WIDTH = 0xB21
    const val GL_LINK_STATUS = 0x8B82
    const val GL_LOW_FLOAT = 0x8DF0
    const val GL_LOW_INT = 0x8DF3
    const val GL_LUMINANCE = 0x1909
    const val GL_LUMINANCE_ALPHA = 0x190A
    const val GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS = 0x8B4D
    const val GL_MAX_CUBE_MAP_TEXTURE_SIZE = 0x851C
    const val GL_MAX_FRAGMENT_UNIFORM_VECTORS = 0x8DFD
    const val GL_MAX_RENDERBUFFER_SIZE = 0x84E8
    const val GL_MAX_TEXTURE_IMAGE_UNITS = 0x8872
    const val GL_MAX_TEXTURE_SIZE = 0xD33
    const val GL_MAX_VARYING_VECTORS = 0x8DFC
    const val GL_MAX_VERTEX_ATTRIBS = 0x8869
    const val GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS = 0x8B4C
    const val GL_MAX_VERTEX_UNIFORM_VECTORS = 0x8DFB
    const val GL_MAX_VIEWPORT_DIMS = 0xD3A
    const val GL_MEDIUM_FLOAT = 0x8DF1
    const val GL_MEDIUM_INT = 0x8DF4
    const val GL_MIRRORED_REPEAT = 0x8370
    const val GL_NEAREST = 0x2600
    const val GL_NEAREST_MIPMAP_LINEAR = 0x2702
    const val GL_NEAREST_MIPMAP_NEAREST = 0x2700
    const val GL_NEVER = 0x200
    const val GL_NICEST = 0x1102
    const val GL_NONE = 0
    const val GL_NOTEQUAL = 0x205
    const val GL_NO_ERROR = 0
    const val GL_NUM_COMPRESSED_TEXTURE_FORMATS = 0x86A2
    const val GL_ONE = 1
    const val GL_ONE_MINUS_CONSTANT_ALPHA = 0x8004
    const val GL_ONE_MINUS_CONSTANT_COLOR = 0x8002
    const val GL_ONE_MINUS_DST_ALPHA = 0x305
    const val GL_ONE_MINUS_DST_COLOR = 0x307
    const val GL_ONE_MINUS_SRC_ALPHA = 0x303
    const val GL_ONE_MINUS_SRC_COLOR = 0x301
    const val GL_OUT_OF_MEMORY = 0x505
    const val GL_PACK_ALIGNMENT = 0xD05
    const val GL_POINTS = 0x0
    const val GL_POLYGON_OFFSET_FACTOR = 0x8038
    const val GL_POLYGON_OFFSET_FILL = 0x8037
    const val GL_POLYGON_OFFSET_UNITS = 0x2A00
    const val GL_RED_BITS = 0xD52
    const val GL_RENDERBUFFER = 0x8D41
    const val GL_RENDERBUFFER_ALPHA_SIZE = 0x8D53
    const val GL_RENDERBUFFER_BINDING = 0x8CA7
    const val GL_RENDERBUFFER_BLUE_SIZE = 0x8D52
    const val GL_RENDERBUFFER_DEPTH_SIZE = 0x8D54
    const val GL_RENDERBUFFER_GREEN_SIZE = 0x8D51
    const val GL_RENDERBUFFER_HEIGHT = 0x8D43
    const val GL_RENDERBUFFER_INTERNAL_FORMAT = 0x8D44
    const val GL_RENDERBUFFER_RED_SIZE = 0x8D50
    const val GL_RENDERBUFFER_STENCIL_SIZE = 0x8D55
    const val GL_RENDERBUFFER_WIDTH = 0x8D42
    const val GL_RENDERER = 0x1F01
    const val GL_REPEAT = 0x2901
    const val GL_REPLACE = 0x1E01
    const val GL_RGB = 0x1907
    const val GL_RGB565 = 0x8D62
    const val GL_RGB5_A1 = 0x8057
    const val GL_RGBA = 0x1908
    const val GL_RGBA4 = 0x8056
    const val GL_SAMPLE_ALPHA_TO_COVERAGE = 0x809E
    const val GL_SAMPLE_BUFFERS = 0x80A8
    const val GL_SAMPLE_COVERAGE = 0x80A0
    const val GL_SAMPLE_COVERAGE_INVERT = 0x80AB
    const val GL_SAMPLE_COVERAGE_VALUE = 0x80AA
    const val GL_SAMPLER_2D = 0x8B5E
    const val GL_SAMPLER_CUBE = 0x8B60
    const val GL_SAMPLES = 0x80A9
    const val GL_SCISSOR_BOX = 0xC10
    const val GL_SCISSOR_TEST = 0xC11
    const val GL_SHADING_LANGUAGE_VERSION = 0x8B8C
    const val GL_SHORT = 0x1402
    const val GL_SRC_ALPHA = 0x302
    const val GL_SRC_ALPHA_SATURATE = 0x308
    const val GL_SRC_COLOR = 0x300
    const val GL_STATIC_DRAW = 0x88E4
    const val GL_STENCIL_ATTACHMENT = 0x8D20
    const val GL_STENCIL_BACK_FAIL = 0x8801
    const val GL_STENCIL_BACK_FUNC = 0x8800
    const val GL_STENCIL_BACK_PASS_DEPTH_FAIL = 0x8802
    const val GL_STENCIL_BACK_PASS_DEPTH_PASS = 0x8803
    const val GL_STENCIL_BACK_REF = 0x8CA3
    const val GL_STENCIL_BACK_VALUE_MASK = 0x8CA4
    const val GL_STENCIL_BACK_WRITEMASK = 0x8CA5
    const val GL_STENCIL_BITS = 0xD57
    const val GL_STENCIL_BUFFER_BIT = 0x400
    const val GL_STENCIL_CLEAR_VALUE = 0xB91
    const val GL_STENCIL_FAIL = 0xB94
    const val GL_STENCIL_FUNC = 0xB92
    const val GL_STENCIL_INDEX = 0x1901
    const val GL_STENCIL_INDEX8 = 0x8D48
    const val GL_STENCIL_PASS_DEPTH_FAIL = 0xB95
    const val GL_STENCIL_PASS_DEPTH_PASS = 0xB96
    const val GL_STENCIL_REF = 0xB97
    const val GL_STENCIL_TEST = 0xB90
    const val GL_STENCIL_VALUE_MASK = 0xB93
    const val GL_STENCIL_WRITEMASK = 0xB98
    const val GL_STREAM_DRAW = 0x88E0
    const val GL_SUBPIXEL_BITS = 0xD50
    const val GL_TEXTURE = 0x1702
    const val GL_TEXTURE0 = 0x84C0
    const val GL_TEXTURE1 = 0x84C1
    const val GL_TEXTURE2 = 0x84C2
    const val GL_TEXTURE3 = 0x84C3
    const val GL_TEXTURE_2D = 0xDE1
    const val GL_TEXTURE_BINDING_2D = 0x8069
    const val GL_TEXTURE_BINDING_CUBE_MAP = 0x8514
    const val GL_TEXTURE_CUBE_MAP = 0x8513
    const val GL_TEXTURE_CUBE_MAP_NEGATIVE_X = 0x8516
    const val GL_TEXTURE_CUBE_MAP_NEGATIVE_Y = 0x8518
    const val GL_TEXTURE_CUBE_MAP_NEGATIVE_Z = 0x851A
    const val GL_TEXTURE_CUBE_MAP_POSITIVE_X = 0x8515
    const val GL_TEXTURE_CUBE_MAP_POSITIVE_Y = 0x8517
    const val GL_TEXTURE_CUBE_MAP_POSITIVE_Z = 0x8519
    const val GL_TEXTURE_MAG_FILTER = 0x2800
    const val GL_TEXTURE_MIN_FILTER = 0x2801
    const val GL_TEXTURE_WRAP_S = 0x2802
    const val GL_TEXTURE_WRAP_T = 0x2803
    const val GL_TRIANGLES = 0x4
    const val GL_TRIANGLE_FAN = 0x6
    const val GL_TRIANGLE_STRIP = 0x5
    const val GL_TRUE = 1
    const val GL_UNPACK_ALIGNMENT = 0xCF5
    const val GL_UNSIGNED_BYTE = 0x1401
    const val GL_UNSIGNED_INT = 0x1405
    const val GL_UNSIGNED_SHORT = 0x1403
    const val GL_UNSIGNED_SHORT_4_4_4_4 = 0x8033
    const val GL_UNSIGNED_SHORT_5_5_5_1 = 0x8034
    const val GL_UNSIGNED_SHORT_5_6_5 = 0x8363
    const val GL_VALIDATE_STATUS = 0x8B83
    const val GL_VENDOR = 0x1F00
    const val GL_VERSION = 0x1F02
    const val GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING = 0x889F
    const val GL_VERTEX_ATTRIB_ARRAY_ENABLED = 0x8622
    const val GL_VERTEX_ATTRIB_ARRAY_NORMALIZED = 0x886A
    const val GL_VERTEX_ATTRIB_ARRAY_POINTER = 0x8645
    const val GL_VERTEX_ATTRIB_ARRAY_SIZE = 0x8623
    const val GL_VERTEX_ATTRIB_ARRAY_STRIDE = 0x8624
    const val GL_VERTEX_ATTRIB_ARRAY_TYPE = 0x8625
    const val GL_VERTEX_SHADER = 0x8B31
    const val GL_VIEWPORT = 0xBA2
    const val GL_ZERO = 0

    @JvmStatic fun glActiveTexture(texture: Int) {}
    @JvmStatic fun glAttachShader(program: Int, shader: Int) {}
    @JvmStatic fun glBindAttribLocation(program: Int, index: Int, name: String?) {}
    @JvmStatic fun glBindBuffer(target: Int, buffer: Int) {}
    @JvmStatic fun glBindFramebuffer(target: Int, framebuffer: Int) {}
    @JvmStatic fun glBindRenderbuffer(target: Int, renderbuffer: Int) {}
    @JvmStatic fun glBindTexture(target: Int, texture: Int) {}
    @JvmStatic fun glBlendColor(red: Float, green: Float, blue: Float, alpha: Float) {}
    @JvmStatic fun glBlendEquation(mode: Int) {}
    @JvmStatic fun glBlendEquationSeparate(modeRGB: Int, modeAlpha: Int) {}
    @JvmStatic fun glBlendFunc(sfactor: Int, dfactor: Int) {}
    @JvmStatic fun glBlendFuncSeparate(srcRGB: Int, dstRGB: Int, srcAlpha: Int, dstAlpha: Int) {}
    @JvmStatic fun glBufferData(target: Int, size: Int, data: java.nio.Buffer?, usage: Int) {}
    @JvmStatic fun glBufferSubData(target: Int, offset: Int, size: Int, data: java.nio.Buffer?) {}
    @JvmStatic fun glCheckFramebufferStatus(target: Int): Int = GL_FRAMEBUFFER_COMPLETE
    @JvmStatic fun glClear(mask: Int) {}
    @JvmStatic fun glClearColor(red: Float, green: Float, blue: Float, alpha: Float) {}
    @JvmStatic fun glClearDepthf(depth: Float) {}
    @JvmStatic fun glClearStencil(s: Int) {}
    @JvmStatic fun glColorMask(red: Boolean, green: Boolean, blue: Boolean, alpha: Boolean) {}
    @JvmStatic fun glCompileShader(shader: Int) {}
    @JvmStatic fun glCompressedTexImage2D(target: Int, level: Int, internalformat: Int, width: Int, height: Int, border: Int, imageSize: Int, data: java.nio.Buffer?) {}
    @JvmStatic fun glCompressedTexSubImage2D(target: Int, level: Int, xoffset: Int, yoffset: Int, width: Int, height: Int, format: Int, imageSize: Int, data: java.nio.Buffer?) {}
    @JvmStatic fun glCopyTexImage2D(target: Int, level: Int, internalformat: Int, x: Int, y: Int, width: Int, height: Int, border: Int) {}
    @JvmStatic fun glCopyTexSubImage2D(target: Int, level: Int, xoffset: Int, yoffset: Int, x: Int, y: Int, width: Int, height: Int) {}
    @JvmStatic fun glCreateProgram(): Int = 1
    @JvmStatic fun glCreateShader(type: Int): Int = 1
    @JvmStatic fun glCullFace(mode: Int) {}
    @JvmStatic fun glDeleteBuffers(n: Int, buffers: IntArray?, offset: Int) {}
    @JvmStatic fun glDeleteBuffers(n: Int, buffers: java.nio.IntBuffer?) {}
    @JvmStatic fun glDeleteFramebuffers(n: Int, framebuffers: IntArray?, offset: Int) {}
    @JvmStatic fun glDeleteFramebuffers(n: Int, framebuffers: java.nio.IntBuffer?) {}
    @JvmStatic fun glDeleteProgram(program: Int) {}
    @JvmStatic fun glDeleteRenderbuffers(n: Int, renderbuffers: IntArray?, offset: Int) {}
    @JvmStatic fun glDeleteRenderbuffers(n: Int, renderbuffers: java.nio.IntBuffer?) {}
    @JvmStatic fun glDeleteShader(shader: Int) {}
    @JvmStatic fun glDeleteTextures(n: Int, textures: IntArray?, offset: Int) {}
    @JvmStatic fun glDeleteTextures(n: Int, textures: java.nio.IntBuffer?) {}
    @JvmStatic fun glDepthFunc(func: Int) {}
    @JvmStatic fun glDepthMask(flag: Boolean) {}
    @JvmStatic fun glDepthRangef(zNear: Float, zFar: Float) {}
    @JvmStatic fun glDetachShader(program: Int, shader: Int) {}
    @JvmStatic fun glDisable(cap: Int) {}
    @JvmStatic fun glDisableVertexAttribArray(index: Int) {}
    @JvmStatic fun glDrawArrays(mode: Int, first: Int, count: Int) {}
    @JvmStatic fun glDrawElements(mode: Int, count: Int, type: Int, indices: java.nio.Buffer?) {}
    @JvmStatic fun glDrawElements(mode: Int, count: Int, type: Int, offset: Int) {}
    @JvmStatic fun glEnable(cap: Int) {}
    @JvmStatic fun glEnableVertexAttribArray(index: Int) {}
    @JvmStatic fun glFinish() {}
    @JvmStatic fun glFlush() {}
    @JvmStatic fun glFramebufferRenderbuffer(target: Int, attachment: Int, renderbuffertarget: Int, renderbuffer: Int) {}
    @JvmStatic fun glFramebufferTexture2D(target: Int, attachment: Int, textarget: Int, texture: Int, level: Int) {}
    @JvmStatic fun glFrontFace(mode: Int) {}
    @JvmStatic fun glGenBuffers(n: Int, buffers: IntArray, offset: Int) { for (i in 0 until n) if (buffers != null && offset + i < buffers.size) buffers[offset + i] = i + 1 }
    @JvmStatic fun glGenBuffers(n: Int, buffers: java.nio.IntBuffer?) { if (buffers != null) for (i in 0 until n) buffers.put(i + 1) }
    @JvmStatic fun glGenFramebuffers(n: Int, framebuffers: IntArray, offset: Int) { for (i in 0 until n) if (framebuffers != null && offset + i < framebuffers.size) framebuffers[offset + i] = i + 1 }
    @JvmStatic fun glGenFramebuffers(n: Int, framebuffers: java.nio.IntBuffer?) { if (framebuffers != null) for (i in 0 until n) framebuffers.put(i + 1) }
    @JvmStatic fun glGenRenderbuffers(n: Int, renderbuffers: IntArray, offset: Int) { for (i in 0 until n) if (renderbuffers != null && offset + i < renderbuffers.size) renderbuffers[offset + i] = i + 1 }
    @JvmStatic fun glGenRenderbuffers(n: Int, renderbuffers: java.nio.IntBuffer?) { if (renderbuffers != null) for (i in 0 until n) renderbuffers.put(i + 1) }
    @JvmStatic fun glGenTextures(n: Int, textures: IntArray, offset: Int) { for (i in 0 until n) if (textures != null && offset + i < textures.size) textures[offset + i] = i + 1 }
    @JvmStatic fun glGenTextures(n: Int, textures: java.nio.IntBuffer?) { if (textures != null) for (i in 0 until n) textures.put(i + 1) }
    @JvmStatic fun glGenerateMipmap(target: Int) {}
    @JvmStatic fun glGetActiveAttrib(program: Int, index: Int, bufsize: Int, length: IntArray?, size: IntArray?, type: IntArray?, name: ByteArray) {}
    @JvmStatic fun glGetActiveUniform(program: Int, index: Int, bufsize: Int, length: IntArray?, size: IntArray?, type: IntArray?, name: ByteArray) {}
    @JvmStatic fun glGetAttachedShaders(program: Int, maxcount: Int, count: IntArray?, shaders: IntArray?) {}
    @JvmStatic fun glGetAttribLocation(program: Int, name: String?): Int = 0
    @JvmStatic fun glGetBooleanv(pname: Int, params: java.nio.ByteBuffer?) {}
    @JvmStatic fun glGetBufferParameteriv(target: Int, pname: Int, params: IntArray?, offset: Int) { if (params != null && params.isNotEmpty()) params[0] = 1 }
    @JvmStatic fun glGetBufferParameteriv(target: Int, pname: Int, params: java.nio.IntBuffer?) { if (params != null) params.put(0, 1) }
    @JvmStatic fun glGetError(): Int = GL_NO_ERROR
    @JvmStatic fun glGetFloatv(pname: Int, params: FloatArray?, offset: Int) { if (params != null && params.isNotEmpty()) params[0] = 1f }
    @JvmStatic fun glGetFloatv(pname: Int, params: java.nio.FloatBuffer?) { if (params != null) params.put(0, 1f) }
    @JvmStatic fun glGetFramebufferAttachmentParameteriv(target: Int, attachment: Int, pname: Int, params: IntArray?, offset: Int) { if (params != null && params.isNotEmpty()) params[0] = 1 }
    @JvmStatic fun glGetFramebufferAttachmentParameteriv(target: Int, attachment: Int, pname: Int, params: java.nio.IntBuffer?) { if (params != null) params.put(0, 1) }
    @JvmStatic fun glGetIntegerv(pname: Int, params: IntArray?, offset: Int) { if (params != null && params.isNotEmpty()) params[0] = 1 }
    @JvmStatic fun glGetIntegerv(pname: Int, params: java.nio.IntBuffer?) { if (params != null) params.put(0, 1) }
    @JvmStatic fun glGetProgramInfoLog(program: Int): String = ""
    @JvmStatic fun glGetProgramiv(program: Int, pname: Int, params: IntArray?, offset: Int) { if (params != null && params.isNotEmpty()) params[0] = 1 }
    @JvmStatic fun glGetProgramiv(program: Int, pname: Int, params: java.nio.IntBuffer?) { if (params != null) params.put(0, 1) }
    @JvmStatic fun glGetRenderbufferParameteriv(target: Int, pname: Int, params: IntArray?, offset: Int) { if (params != null && params.isNotEmpty()) params[0] = 1 }
    @JvmStatic fun glGetRenderbufferParameteriv(target: Int, pname: Int, params: java.nio.IntBuffer?) { if (params != null) params.put(0, 1) }
    @JvmStatic fun glGetShaderInfoLog(shader: Int): String = ""
    @JvmStatic fun glGetShaderPrecisionFormat(shadertype: Int, precisiontype: Int, range: IntArray?, precision: IntArray?) {}
    @JvmStatic fun glGetShaderiv(shader: Int, pname: Int, params: IntArray?, offset: Int) { if (params != null && params.isNotEmpty()) params[0] = 1 }
    @JvmStatic fun glGetShaderiv(shader: Int, pname: Int, params: java.nio.IntBuffer?) { if (params != null) params.put(0, 1) }
    @JvmStatic fun glGetString(name: Int): String = ""
    @JvmStatic fun glGetTexParameterfv(target: Int, pname: Int, params: FloatArray?, offset: Int) {}
    @JvmStatic fun glGetTexParameterfv(target: Int, pname: Int, params: java.nio.FloatBuffer?) {}
    @JvmStatic fun glGetTexParameteriv(target: Int, pname: Int, params: IntArray?, offset: Int) { if (params != null && params.isNotEmpty()) params[0] = 1 }
    @JvmStatic fun glGetTexParameteriv(target: Int, pname: Int, params: java.nio.IntBuffer?) { if (params != null) params.put(0, 1) }
    @JvmStatic fun glGetUniformfv(program: Int, location: Int, params: FloatArray?, offset: Int) {}
    @JvmStatic fun glGetUniformfv(program: Int, location: Int, params: java.nio.FloatBuffer?) {}
    @JvmStatic fun glGetUniformiv(program: Int, location: Int, params: IntArray?, offset: Int) {}
    @JvmStatic fun glGetUniformiv(program: Int, location: Int, params: java.nio.IntBuffer?) {}
    @JvmStatic fun glGetUniformLocation(program: Int, name: String?): Int = 0
    @JvmStatic fun glGetVertexAttribfv(index: Int, pname: Int, params: FloatArray?, offset: Int) {}
    @JvmStatic fun glGetVertexAttribfv(index: Int, pname: Int, params: java.nio.FloatBuffer?) {}
    @JvmStatic fun glGetVertexAttribiv(index: Int, pname: Int, params: IntArray?, offset: Int) {}
    @JvmStatic fun glGetVertexAttribiv(index: Int, pname: Int, params: java.nio.IntBuffer?) {}
    @JvmStatic fun glGetVertexAttribPointerv(index: Int, pname: Int, pointer: java.nio.Buffer?) {}
    @JvmStatic fun glHint(target: Int, mode: Int) {}
    @JvmStatic fun glIsBuffer(buffer: Int): Boolean = false
    @JvmStatic fun glIsEnabled(cap: Int): Boolean = false
    @JvmStatic fun glIsFramebuffer(framebuffer: Int): Boolean = false
    @JvmStatic fun glIsProgram(program: Int): Boolean = true
    @JvmStatic fun glIsRenderbuffer(renderbuffer: Int): Boolean = false
    @JvmStatic fun glIsShader(shader: Int): Boolean = true
    @JvmStatic fun glIsTexture(texture: Int): Boolean = true
    @JvmStatic fun glLineWidth(width: Float) {}
    @JvmStatic fun glLinkProgram(program: Int) {}
    @JvmStatic fun glPixelStorei(pname: Int, param: Int) {}
    @JvmStatic fun glPolygonOffset(factor: Float, units: Float) {}
    @JvmStatic fun glReadPixels(x: Int, y: Int, width: Int, height: Int, format: Int, type: Int, pixels: java.nio.Buffer?) {}
    @JvmStatic fun glReadPixels(x: Int, y: Int, width: Int, height: Int, format: Int, type: Int, pixels: IntArray?, offset: Int) {}
    @JvmStatic fun glReleaseShaderCompiler() {}
    @JvmStatic fun glRenderbufferStorage(target: Int, internalformat: Int, width: Int, height: Int) {}
    @JvmStatic fun glSampleCoverage(value: Float, invert: Boolean) {}
    @JvmStatic fun glScissor(x: Int, y: Int, width: Int, height: Int) {}
    @JvmStatic fun glShaderBinary(n: Int, shaders: IntArray?, offset: Int, binary: java.nio.Buffer?, length: Int) {}
    @JvmStatic fun glShaderSource(shader: Int, string: String?) {}
    @JvmStatic fun glStencilFunc(func: Int, ref: Int, mask: Int) {}
    @JvmStatic fun glStencilFuncSeparate(face: Int, func: Int, ref: Int, mask: Int) {}
    @JvmStatic fun glStencilMask(mask: Int) {}
    @JvmStatic fun glStencilMaskSeparate(face: Int, mask: Int) {}
    @JvmStatic fun glStencilOp(fail: Int, zfail: Int, zpass: Int) {}
    @JvmStatic fun glStencilOpSeparate(face: Int, fail: Int, zfail: Int, zpass: Int) {}
    @JvmStatic fun glTexImage2D(target: Int, level: Int, internalformat: Int, width: Int, height: Int, border: Int, format: Int, type: Int, pixels: java.nio.Buffer?) {}
    @JvmStatic fun glTexParameterf(target: Int, pname: Int, param: Float) {}
    @JvmStatic fun glTexParameterfv(target: Int, pname: Int, params: FloatArray?, offset: Int) {}
    @JvmStatic fun glTexParameterfv(target: Int, pname: Int, params: java.nio.FloatBuffer?) {}
    @JvmStatic fun glTexParameteri(target: Int, pname: Int, param: Int) {}
    @JvmStatic fun glTexParameteriv(target: Int, pname: Int, params: IntArray?, offset: Int) {}
    @JvmStatic fun glTexParameteriv(target: Int, pname: Int, params: java.nio.IntBuffer?) {}
    @JvmStatic fun glTexSubImage2D(target: Int, level: Int, xoffset: Int, yoffset: Int, width: Int, height: Int, format: Int, type: Int, pixels: java.nio.Buffer?) {}
    @JvmStatic fun glUniform1f(location: Int, x: Float) {}
    @JvmStatic fun glUniform1fv(location: Int, count: Int, v: FloatArray?, offset: Int) {}
    @JvmStatic fun glUniform1fv(location: Int, count: Int, v: java.nio.FloatBuffer?) {}
    @JvmStatic fun glUniform1i(location: Int, x: Int) {}
    @JvmStatic fun glUniform1iv(location: Int, count: Int, v: IntArray?, offset: Int) {}
    @JvmStatic fun glUniform1iv(location: Int, count: Int, v: java.nio.IntBuffer?) {}
    @JvmStatic fun glUniform2f(location: Int, x: Float, y: Float) {}
    @JvmStatic fun glUniform2fv(location: Int, count: Int, v: FloatArray?, offset: Int) {}
    @JvmStatic fun glUniform2fv(location: Int, count: Int, v: java.nio.FloatBuffer?) {}
    @JvmStatic fun glUniform2i(location: Int, x: Int, y: Int) {}
    @JvmStatic fun glUniform2iv(location: Int, count: Int, v: IntArray?, offset: Int) {}
    @JvmStatic fun glUniform2iv(location: Int, count: Int, v: java.nio.IntBuffer?) {}
    @JvmStatic fun glUniform3f(location: Int, x: Float, y: Float, z: Float) {}
    @JvmStatic fun glUniform3fv(location: Int, count: Int, v: FloatArray?, offset: Int) {}
    @JvmStatic fun glUniform3fv(location: Int, count: Int, v: java.nio.FloatBuffer?) {}
    @JvmStatic fun glUniform3i(location: Int, x: Int, y: Int, z: Int) {}
    @JvmStatic fun glUniform3iv(location: Int, count: Int, v: IntArray?, offset: Int) {}
    @JvmStatic fun glUniform3iv(location: Int, count: Int, v: java.nio.IntBuffer?) {}
    @JvmStatic fun glUniform4f(location: Int, x: Float, y: Float, z: Float, w: Float) {}
    @JvmStatic fun glUniform4fv(location: Int, count: Int, v: FloatArray?, offset: Int) {}
    @JvmStatic fun glUniform4fv(location: Int, count: Int, v: java.nio.FloatBuffer?) {}
    @JvmStatic fun glUniform4i(location: Int, x: Int, y: Int, z: Int, w: Int) {}
    @JvmStatic fun glUniform4iv(location: Int, count: Int, v: IntArray?, offset: Int) {}
    @JvmStatic fun glUniform4iv(location: Int, count: Int, v: java.nio.IntBuffer?) {}
    @JvmStatic fun glUniformMatrix2fv(location: Int, count: Int, transpose: Boolean, value: FloatArray?, offset: Int) {}
    @JvmStatic fun glUniformMatrix2fv(location: Int, count: Int, transpose: Boolean, value: java.nio.FloatBuffer?) {}
    @JvmStatic fun glUniformMatrix3fv(location: Int, count: Int, transpose: Boolean, value: FloatArray?, offset: Int) {}
    @JvmStatic fun glUniformMatrix3fv(location: Int, count: Int, transpose: Boolean, value: java.nio.FloatBuffer?) {}
    @JvmStatic fun glUniformMatrix4fv(location: Int, count: Int, transpose: Boolean, value: FloatArray?, offset: Int) {}
    @JvmStatic fun glUniformMatrix4fv(location: Int, count: Int, transpose: Boolean, value: java.nio.FloatBuffer?) {}
    @JvmStatic fun glUseProgram(program: Int) {}
    @JvmStatic fun glValidateProgram(program: Int) {}
    @JvmStatic fun glVertexAttrib1f(indx: Int, x: Float) {}
    @JvmStatic fun glVertexAttrib1fv(indx: Int, values: FloatArray?, offset: Int) {}
    @JvmStatic fun glVertexAttrib1fv(indx: Int, values: java.nio.FloatBuffer?) {}
    @JvmStatic fun glVertexAttrib2f(indx: Int, x: Float, y: Float) {}
    @JvmStatic fun glVertexAttrib2fv(indx: Int, values: FloatArray?, offset: Int) {}
    @JvmStatic fun glVertexAttrib2fv(indx: Int, values: java.nio.FloatBuffer?) {}
    @JvmStatic fun glVertexAttrib3f(indx: Int, x: Float, y: Float, z: Float) {}
    @JvmStatic fun glVertexAttrib3fv(indx: Int, values: FloatArray?, offset: Int) {}
    @JvmStatic fun glVertexAttrib3fv(indx: Int, values: java.nio.FloatBuffer?) {}
    @JvmStatic fun glVertexAttrib4f(indx: Int, x: Float, y: Float, z: Float, w: Float) {}
    @JvmStatic fun glVertexAttrib4fv(indx: Int, values: FloatArray?, offset: Int) {}
    @JvmStatic fun glVertexAttrib4fv(indx: Int, values: java.nio.FloatBuffer?) {}
    @JvmStatic fun glVertexAttribPointer(indx: Int, size: Int, type: Int, normalized: Boolean, stride: Int, ptr: java.nio.Buffer?) {}
    @JvmStatic fun glVertexAttribPointer(indx: Int, size: Int, type: Int, normalized: Boolean, stride: Int, offset: Int) {}
    @JvmStatic fun glViewport(x: Int, y: Int, width: Int, height: Int) {}
}

/** Static image helpers. */
object GLUtils {
    @JvmStatic fun texImage2D(target: Int, level: Int, bitmap: Any?, type: Int) {}
    @JvmStatic fun texSubImage2D(target: Int, level: Int, xoffset: Int, yoffset: Int, bitmap: Any?) {}
    @JvmStatic fun getType(bitmap: Any?): Int = GLES20.GL_UNSIGNED_BYTE
    @JvmStatic fun getInternalFormat(bitmap: Any?): Int = GLES20.GL_RGBA
}

/** 4x4 float-matrix maths (column-major, matches the real android.opengl.Matrix). */
object Matrix {
    @JvmStatic fun setIdentityM(sm: FloatArray, smOffset: Int) {
        for (i in 0 until 16) sm[smOffset + i] = if (i % 5 == 0) 1f else 0f
    }

    @JvmStatic fun orthoM(m: FloatArray, mOffset: Int, left: Float, right: Float, bottom: Float, top: Float, near: Float, far: Float) {
        if (left == right || bottom == top || near == far) return
        val rWidth = 1.0f / (right - left)
        val rHeight = 1.0f / (top - bottom)
        val rDepth = 1.0f / (far - near)
        val tX = (right + left) * rWidth
        val tY = (top + bottom) * rHeight
        val tZ = -(far + near) * rDepth
        setIdentityM(m, mOffset)
        m[mOffset] = 2f * rWidth
        m[mOffset + 5] = 2f * rHeight
        m[mOffset + 10] = -2f * rDepth
        m[mOffset + 12] = tX
        m[mOffset + 13] = tY
        m[mOffset + 14] = tZ
    }

    @JvmStatic fun frustumM(m: FloatArray, offset: Int, left: Float, right: Float, bottom: Float, top: Float, near: Float, far: Float) {
        if (left == right || top == bottom || near == far) return
        if (near <= 0f || far <= 0f) return
        val rWidth = 1.0f / (right - left)
        val rHeight = 1.0f / (top - bottom)
        val rDepth = 1.0f / (near - far)
        val x = 2.0f * (near * rWidth)
        val y = 2.0f * (near * rHeight)
        val z = (far + near) * rDepth
        val A = (right + left) * rWidth
        val B = (top + bottom) * rHeight
        setIdentityM(m, offset)
        m[offset] = x; m[offset + 5] = y; m[offset + 8] = A; m[offset + 9] = B
        m[offset + 10] = z; m[offset + 11] = -1f; m[offset + 14] = 2.0f * (far * near * rDepth)
    }

    @JvmStatic fun perspectiveM(m: FloatArray, offset: Int, fovy: Float, aspect: Float, zNear: Float, zFar: Float) {
        val f = (1.0 / Math.tan(fovy * Math.PI / 360.0)).toFloat()
        val nf = 1.0f / (zNear - zFar)
        setIdentityM(m, offset)
        m[offset] = f / aspect
        m[offset + 5] = f
        m[offset + 10] = (zFar + zNear) * nf
        m[offset + 11] = -1f
        m[offset + 14] = 2f * zFar * zNear * nf
        m[offset + 15] = 0f
    }

    @JvmStatic fun setLookAtM(rm: FloatArray, rmOffset: Int, eyeX: Float, eyeY: Float, eyeZ: Float,
                              centerX: Float, centerY: Float, centerZ: Float, upX: Float, upY: Float, upZ: Float) {
        var fx = centerX - eyeX
        var fy = centerY - eyeY
        var fz = centerZ - eyeZ
        var rlf = 1.0f / MatrixLength(fx, fy, fz)
        fx *= rlf; fy *= rlf; fz *= rlf
        var sx = fy * upZ - fz * upY
        var sy = fz * upX - fx * upZ
        var sz = fx * upY - fy * upX
        val rls = 1.0f / MatrixLength(sx, sy, sz)
        sx *= rls; sy *= rls; sz *= rls
        val ux = sy * fz - sz * fy
        val uy = sz * fx - sx * fz
        val uz = sx * fy - sy * fx
        rm[rmOffset] = sx; rm[rmOffset + 1] = ux; rm[rmOffset + 2] = -fx; rm[rmOffset + 3] = 0f
        rm[rmOffset + 4] = sy; rm[rmOffset + 5] = uy; rm[rmOffset + 6] = -fy; rm[rmOffset + 7] = 0f
        rm[rmOffset + 8] = sz; rm[rmOffset + 9] = uz; rm[rmOffset + 10] = -fz; rm[rmOffset + 11] = 0f
        rm[rmOffset + 12] = 0f; rm[rmOffset + 13] = 0f; rm[rmOffset + 14] = 0f; rm[rmOffset + 15] = 1f
        translateM(rm, rmOffset, -eyeX, -eyeY, -eyeZ)
    }

    private fun MatrixLength(x: Float, y: Float, z: Float): Float {
        val l = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        return if (l == 0f) 1f else l
    }

    @JvmStatic fun translateM(m: FloatArray, mOffset: Int, x: Float, y: Float, z: Float) {
        for (i in 0 until 4) {
            val mi = mOffset + i
            m[mi + 12] += m[mi] * x + m[mi + 4] * y + m[mi + 8] * z
        }
    }

    @JvmStatic fun scaleM(m: FloatArray, mOffset: Int, x: Float, y: Float, z: Float) {
        for (i in 0 until 4) {
            val mi = mOffset + i
            m[mi] *= x; m[mi + 4] *= y; m[mi + 8] *= z
        }
    }

    @JvmStatic fun rotateM(m: FloatArray, mOffset: Int, a: Float, x: Float, y: Float, z: Float) {
        val r = FloatArray(16)
        setRotateM(r, 0, a, x, y, z)
        // real: m = r * m
        val tmp = FloatArray(16)
        multiplyMM(tmp, 0, r, 0, m, mOffset)
        System.arraycopy(tmp, 0, m, mOffset, 16)
    }

    @JvmStatic fun setRotateM(rm: FloatArray, rmOffset: Int, a: Float, x: Float, y: Float, z: Float) {
        setIdentityM(rm, rmOffset)
        val len = MatrixLength(x, y, z)
        val s = Math.sin(a * Math.PI / 180.0).toFloat()
        val c = Math.cos(a * Math.PI / 180.0).toFloat()
        val nc = 1f - c
        rm[rmOffset] = x * x * nc + c
        rm[rmOffset + 1] = y * x * nc + z * s
        rm[rmOffset + 2] = z * x * nc - y * s
        rm[rmOffset + 4] = x * y * nc - z * s
        rm[rmOffset + 5] = y * y * nc + c
        rm[rmOffset + 6] = z * y * nc + x * s
        rm[rmOffset + 8] = x * z * nc + y * s
        rm[rmOffset + 9] = y * z * nc - x * s
        rm[rmOffset + 10] = z * z * nc + c
    }

    @JvmStatic fun multiplyMM(result: FloatArray, resultOffset: Int, lhs: FloatArray, lhsOffset: Int, rhs: FloatArray, rhsOffset: Int) {
        val tmp = FloatArray(16)
        for (i in 0 until 4) {
            val r4 = i * 4
            val r30 = rhsOffset + r4
            for (j in 0 until 4) {
                val rj = r4 + j
                tmp[rj] = lhs[lhsOffset + j] * rhs[r30] + lhs[lhsOffset + 4 + j] * rhs[r30 + 1] +
                    lhs[lhsOffset + 8 + j] * rhs[r30 + 2] + lhs[lhsOffset + 12 + j] * rhs[r30 + 3]
            }
        }
        System.arraycopy(tmp, 0, result, resultOffset, 16)
    }

    @JvmStatic fun multiplyMV(resultVec: FloatArray, resultVecOffset: Int, lhsMat: FloatArray, lhsMatOffset: Int, rhsVec: FloatArray, rhsVecOffset: Int) {
        val tmp = FloatArray(4)
        for (i in 0 until 4) {
            tmp[i] = lhsMat[lhsMatOffset + i] * rhsVec[rhsVecOffset] +
                lhsMat[lhsMatOffset + 4 + i] * rhsVec[rhsVecOffset + 1] +
                lhsMat[lhsMatOffset + 8 + i] * rhsVec[rhsVecOffset + 2] +
                lhsMat[lhsMatOffset + 12 + i] * rhsVec[rhsVecOffset + 3]
        }
        System.arraycopy(tmp, 0, resultVec, resultVecOffset, 4)
    }
}

/** Headless GLSurfaceView surface for SceneRenderer. */
class GLSurfaceView {
    interface Renderer {
        fun onSurfaceCreated(gl: GL10?, config: EGLConfig?)
        fun onSurfaceChanged(gl: GL10?, width: Int, height: Int)
        fun onDrawFrame(gl: GL10?)
    }

    companion object {
        const val RENDERMODE_CONTINUOUSLY = 1
        const val RENDERMODE_WHEN_DIRTY = 0
    }

    fun setEGLContextClientVersion(version: Int) {}
    fun setRenderer(renderer: Renderer?) {}
    fun setRenderMode(mode: Int) {}
    fun onPause() {}
    fun onResume() {}
    fun queueEvent(r: Runnable?) {}
    fun requestRender() {}
}
