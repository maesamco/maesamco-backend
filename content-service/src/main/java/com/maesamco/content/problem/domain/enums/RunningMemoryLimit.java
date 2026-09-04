package com.maesamco.content.problem.domain.enums;

public enum RunningMemoryLimit {

    /** 128MB */
    MB_128(128),

    /** 256MB */
    MB_256(256),

    /** 512MB */
    MB_512(512),

    /** 1GB */
    MB_1024(1024),

    /** 2GB */
    MB_2048(2048);

    private final int megabytes;

    RunningMemoryLimit(int megabytes) { this.megabytes = megabytes; }

    public int getMegabytes() { return megabytes; }
}