uint tr_base;
float tr_float(uint offset) { return uintBitsToFloat(tr_data[tr_base + offset]); }
vec4 tr_vector(uint offset) {
    return vec4(tr_float(offset), tr_float(offset + 1u), tr_float(offset + 2u), tr_float(offset + 3u));
}
void main() {
#if TR_MODE == 0
    tr_base = uint(tr_Bone) * 44u;
#else
    tr_base = uint(gl_InstanceID) * 44u;
#endif
    mat4 pose = mat4(tr_vector(0u), tr_vector(4u), tr_vector(8u), tr_vector(12u));
    mat3 normal = mat3(tr_vector(16u).xyz, tr_vector(20u).xyz, tr_vector(24u).xyz);
    tr_Position = (pose * vec4(TR_INPUT_Position, 1.0)).xyz;
#ifdef TR_HAS_Normal
    tr_Normal = tr_float(28u) > 0.5 ? normal * vec3(0.0, 1.0, 0.0) : normal * TR_INPUT_Normal;
#endif
#ifdef TR_HAS_Color
    tr_Color = TR_INPUT_Color * tr_vector(32u);
#endif
#ifdef TR_HAS_UV0
    tr_UV0 = TR_INPUT_UV0;
#endif
#ifdef TR_HAS_UV1
    tr_UV1 = TR_TYPE_UV1(tr_data[tr_base + 38u], tr_data[tr_base + 39u]);
#endif
#ifdef TR_HAS_UV2
    tr_UV2 = TR_TYPE_UV2(max(vec2(TR_INPUT_UV2), vec2(tr_data[tr_base + 36u], tr_data[tr_base + 37u])));
#endif
#ifdef TR_HAS_Entity
    tr_Entity = TR_TYPE_Entity(ivec3(tr_data[tr_base + 40u], tr_data[tr_base + 41u], tr_data[tr_base + 42u]));
#endif
#ifdef TR_HAS_at_tangent
    vec3 tangent = mat3(pose) * TR_INPUT_at_tangent.xyz;
    tr_at_tangent = vec4(length(tangent) > 0.0 ? normalize(tangent) : tangent,
            TR_INPUT_at_tangent.w * sign(determinant(mat3(pose))));
#endif
    tr_original_main();
}
