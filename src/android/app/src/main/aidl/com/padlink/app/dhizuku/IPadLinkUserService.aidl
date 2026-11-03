package com.padlink.app.dhizuku;

/** 跑在 Dhizuku（Device Owner）进程里的最小接口。 */
interface IPadLinkUserService {
    void setStatusBarDisabled(boolean disabled);

    boolean isStatusBarDisabled();
}
