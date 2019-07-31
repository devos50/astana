.class public Lregdepgraph;
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
    const/4 v2, 4
    add-int v3, v2, v1
    return-void
.end method

.method public static method3([Ljava/lang/String;)V
  .locals 15
    const/4 v0, 1
    const/4 v1, 2
    goto :L2
  :L1
    sub-int v2, v1, v0
  :L2
    return-void
.end method

.method public static method4([Ljava/lang/String;)V
  .locals 15
    const/4 v0, 1
    const/4 v1, 2
  :L0
    if-eqz v1, :L1
    sub-int v2, v1, v0
    goto :L0
  :L1
    return-void
.end method

.method public static method5([Ljava/lang/String;)V
  .locals 15
    const/4 v0, 1
    packed-switch v0, :L1
    const/4 v1, 4
    goto :L4
  :L2
    const/4 v2, 2
    goto :L4
  :L3
    const/4 v3, 3
    goto :L4
  :L4
    return-void
  :L1
  .packed-switch 0
    :L2
    :L3
  .end packed-switch
.end method

.method public static method6([Ljava/lang/String;)V
  .locals 15
    const/16 v1, 31930
    const/16 v2, 3479
    const-string v3, "=ZfZ[a"
    invoke-static { }, Lu/VnN;->T()I
    move-result v0
    xor-int/2addr v0, v1
    if-eqz v0 :L1
    add-int/2addr v1, v0
  :L1
    int-to-short v1, v0
    invoke-static { }, Lu/VnN;->T()I
    move-result v0
    xor-int/2addr v0, v2
    int-to-short v0, v0
    invoke-static { v3, v1, v0 }, Lu/NS;->b(Ljava/lang/String;SS)Ljava/lang/String;
    move-result-object v0
.end method