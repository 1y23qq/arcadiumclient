// SPDX-License-Identifier: GPL-3.0-only
#include "../arc_scene_lease.h"
#undef NDEBUG
#include <assert.h>
#include <stddef.h>
#include <stdio.h>
#include <string.h>
int main(void) {
    assert(sizeof(arc_scene_metal_info)==48 && offsetof(arc_scene_metal_info,width)==16
        && offsetof(arc_scene_metal_info,device)==24 && offsetof(arc_scene_metal_info,texture)==40);
#if !defined(__APPLE__)
    void *out=(void *)1;arc_scene_metal_info info;memset(&info,0x5a,sizeof(info));
    assert(arc_scene_metal_lease_abi()==1);
    assert(arc_scene_metal_lease_acquire(NULL,ARC_METAL_DEVICE_LEASE,0,&out)==ARC_UNAVAILABLE&&out==NULL);
    assert(arc_scene_metal_lease_info(NULL,&info)==ARC_UNAVAILABLE);
    const unsigned char *bytes=(const unsigned char *)&info;
    for(size_t i=0;i<sizeof(info);i++)assert(bytes[i]==0);
    assert(arc_scene_metal_lease_release(NULL)==ARC_UNAVAILABLE);
#endif
    puts("Metal lease ABI layout and unsupported rollback passed");return 0;
}
