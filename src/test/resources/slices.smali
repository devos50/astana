.class public Lslices;
.super Ljava/lang/Object;

.method public static method1([Ljava/lang/String;)V
  .locals 15
    const/4 v1, 2
    const/4 v2, 1
    return-void
.end method

.method public static method2([Ljava/lang/String;)V
  .locals 15
    const/4 v1, 2
    const/4 v2, 1
    const/4 v2, 3  # this should not be included
    add-int v3, v1, v2
    return-void
.end method

.method public static method3([Ljava/lang/String;)V
  .locals 15
    const/4 v0, 1
    const/4 v1, 2
    const/4 v2, 5
  :L0
    if-eqz v1, :L1
    const/4 v2, 6
    sub-int/2addr v1, v0
    goto :L0
  :L1
    return-void
.end method

.method public static method4([Ljava/lang/String;)V
  .locals 15
    const/4 v0, 1
    const/4 v1, 2
    const/4 v2, 5
  :L0
    if-eqz v1, :L1
    sub-int/2addr v1, v0
    goto :L0
  :L1
    return-void
.end method

.method public static method5([Ljava/lang/String;)V
  .locals 15
    const-string v2, "YkfV^SSQ?P]\\QVT9MPGPUS4>HP?x\u0015v"
    const/16 v1, -31547
    invoke-static { }, Lu/zx;->l()I
    move-result v0
    xor-int/2addr v0, v1
    int-to-short v6, v0
    invoke-virtual { v2 }, Ljava/lang/String;->length()I
    move-result v0
    new-array v5, v0, [I
    new-instance v4, Lu/xFN;
    invoke-direct { v4, v2 }, Lu/xFN;-><init>(Ljava/lang/String;)V
    const/4 v3, 0
  :L0
    invoke-virtual { v4 }, Lu/xFN;->SJu()Z
    move-result v0
    if-eqz v0, :L1
    invoke-virtual { v4 }, Lu/xFN;->QJu()I
    move-result v0
    const/16 v2, -12345
    invoke-static { v0 }, Lu/Xju;->g(I)Lu/Xju;
    move-result-object v2
    invoke-virtual { v2, v0 }, Lu/Xju;->rPu(I)I
    move-result v1
    add-int v0, v6, v6
    add-int/2addr v0, v3
    add-int/2addr v0, v1
    invoke-virtual { v2, v0 }, Lu/Xju;->ePu(I)I
    move-result v0
    aput v0, v5, v3
    add-int/lit8 v3, v3, 1
    goto :L0
  :L1
    new-instance v1, Ljava/lang/String;
    const/4 v0, 0
    invoke-direct { v1, v5, v0, v3 }, Ljava/lang/String;-><init>([III)V
    return-void
.end method
