package io.github.tomusin.voyager.segments;

public enum DataSegmentType {
    LOGICAL_SCENE_GRAPH(1, "Logical Scene Graph", true),
    JT_B_REP(2, "JT B-Rep", true),
    PMI_DATA(3, "PMI Data", true),
    META_DATA(4, "Meta Data", true),
    SHAPE(6, "Shape", false),
    SHAPE_LOD0(7, "Shape LOD0", false),
    SHAPE_LOD1(8, "Shape LOD1", false),
    SHAPE_LOD2(9, "Shape LOD2", false),
    SHAPE_LOD3(10, "Shape LOD3", false),
    SHAPE_LOD4(11, "Shape LOD4", false),
    SHAPE_LOD5(12, "Shape LOD5", false),
    SHAPE_LOD6(13, "Shape LOD6", false),
    SHAPE_LOD7(14, "Shape LOD7", false),
    SHAPE_LOD8(15, "Shape LOD8", false),
    SHAPE_LOD9(16, "Shape LOD9", false),
    XT_B_REP(17, "XT B-Rep", true),
    WIREFRAME_REPRESENTATION(18, "Wireframe Representation", true),
    ULP(20, "ULP", true),
    STT(23, "STT", true),
    LWPA(24, "LWPA", true),
    MULTI_XT_B_REP(30, "MultiXT B-Rep", true),
    INFO_SEGMENT(31, "InfoSegment", true),
    STEP_B_REP(33, "STEP B-rep", true);

    private final int typeId;
    private final String contents;
    private final boolean compression;

    DataSegmentType(int typeId, String contents, boolean compression) {
        this.typeId = typeId;
        this.contents = contents;
        this.compression = compression;
    }

    public int getTypeId() {
        return typeId;
    }

    public String getContents() {
        return contents;
    }

    public boolean isCompression() {
        return compression;
    }

    public static DataSegmentType getByTypeId(int typeId) {
        for (DataSegmentType type : values()) {
            if (type.getTypeId() == typeId) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid type ID: " + typeId);
    }
}
