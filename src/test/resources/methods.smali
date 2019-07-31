.class public Lcfg;
.super Ljava/lang/Object;

.method public static method1([Ljava/lang/String;)V
  .locals 15
    const/4 v1, 2
    const/4 v2, 1
    return-void
.end method

.method public static method2([Ljava/lang/String;)V
  .locals 15
    const/4 v0, 1
    const/4 v1, 2
  :L0
    if-eqz v1, :L1
    sub-int/2addr v1, v0
    goto :L0
  :L1
    return-void
.end method

.method public static method3([Ljava/lang/String;)V
  .catch Ljava/lang/Exception; { :L0 .. :L1 } :L2
  .registers 15
  :L0
    const/4 v0, 1
    const/4 v1, 1
  :L1
    return-void
  :L2
    move-exception v2
    throw v2
.end method

.method public static method4([Ljava/lang/String;)V
  .locals 15
    const/4 v0, 1
    packed-switch v0, :L1
    const/4 v1, 4
    goto :L4
  :L2
    const/4 v1, 2
    goto :L4
  :L3
    const/4 v1, 3
    goto :L4
  :L4
    return-void
  :L1
  .packed-switch 0
    :L2
    :L3
  .end packed-switch
.end method

.method public final method5(Ljava/lang/String;)V
  .registers 13
    invoke-static { }, Lu/dcN;->g()Lu/dcN;
    move-result-object v2
    const/4 v0, 1
    iput-boolean v0, v2, Lu/dcN;->N:Z
    const-wide/16 v0, 0
    iput-wide v0, v2, Lu/dcN;->u:J
    new-instance v7, Ljava/lang/StringBuilder;
    const-string v2, "YkfV^SSQ?P]\\QVT9MPGPUS4>HP?x\u0015v"  # 7
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
    invoke-virtual { v4 }, Lu/xFN;->SJu()Z  # 20
    move-result v0  # 21
    if-eqz v0, :L1  # 22
    invoke-virtual { v4 }, Lu/xFN;->QJu()I
    move-result v0
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
    goto :L0  # 37
  :L1
    new-instance v1, Ljava/lang/String;  # 39
    const/4 v0, 0  # 40
    invoke-direct { v1, v5, v0, v3 }, Ljava/lang/String;-><init>([III)V  # 41
    invoke-direct { v7, v1 }, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V
    invoke-static { }, Lu/dcN;->l()I
    move-result v0
    invoke-virtual { v7, v0 }, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;
    invoke-virtual { v7 }, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
    move-result-object v8
    invoke-static { }, Lu/NpN;->t()Ljava/lang/String;
    move-result-object v0
    invoke-static { v0 }, Ljava/lang/String;->valueOf(Ljava/lang/Object;)Ljava/lang/String;
    move-result-object v7
    const-string v2, "\u0002"
    const/16 v1, -12377
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
  :L2
    invoke-virtual { v4 }, Lu/xFN;->SJu()Z
    move-result v0
    if-eqz v0, :L3
    invoke-virtual { v4 }, Lu/xFN;->QJu()I
    move-result v0
    invoke-static { v0 }, Lu/Xju;->g(I)Lu/Xju;
    move-result-object v2
    invoke-virtual { v2, v0 }, Lu/Xju;->rPu(I)I
    move-result v1
    add-int v0, v6, v3
    add-int/2addr v0, v1
    invoke-virtual { v2, v0 }, Lu/Xju;->ePu(I)I
    move-result v0
    aput v0, v5, v3
    add-int/lit8 v3, v3, 1
    goto :L2
  :L3
    new-instance v6, Ljava/lang/String;
    const/4 v0, 0
    invoke-direct { v6, v5, v0, v3 }, Ljava/lang/String;-><init>([III)V
    const-string v2, "}2/!+\"^\u0013&56-44"
    const/16 v1, -19170
    invoke-static { }, Lu/zx;->l()I
    move-result v0
    xor-int/2addr v0, v1
    int-to-short v9, v0
    invoke-virtual { v2 }, Ljava/lang/String;->length()I
    move-result v0
    new-array v5, v0, [I
    new-instance v4, Lu/xFN;
    invoke-direct { v4, v2 }, Lu/xFN;-><init>(Ljava/lang/String;)V
    const/4 v3, 0
  :L4
    invoke-virtual { v4 }, Lu/xFN;->SJu()Z
    move-result v0
    if-eqz v0, :L5
    invoke-virtual { v4 }, Lu/xFN;->QJu()I
    move-result v0
    invoke-static { v0 }, Lu/Xju;->g(I)Lu/Xju;
    move-result-object v2
    invoke-virtual { v2, v0 }, Lu/Xju;->rPu(I)I
    move-result v1
    add-int v0, v9, v9
    add-int/2addr v0, v3
    sub-int/2addr v1, v0
    invoke-virtual { v2, v1 }, Lu/Xju;->ePu(I)I
    move-result v0
    aput v0, v5, v3
    add-int/lit8 v3, v3, 1
    goto :L4
  :L5
    new-instance v1, Ljava/lang/String;
    const/4 v0, 0
    invoke-direct { v1, v5, v0, v3 }, Ljava/lang/String;-><init>([III)V
    new-instance v4, Lu/LiN;
    invoke-direct { v4, v7, v6, v1, v8 }, Lu/LiN;-><init>(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V
    new-instance v3, Lu/OEg;
    new-instance v2, Lu/uxu;
    invoke-static { }, Lu/Vtg;->u()Lu/Vtg;
    move-result-object v0
    invoke-direct { v2, v0 }, Lu/uxu;-><init>(Lu/Vtg;)V
    new-instance v1, Lu/YJu;
    invoke-static { }, Lu/Vtg;->u()Lu/Vtg;
    move-result-object v0
    invoke-direct { v1, v0 }, Lu/YJu;-><init>(Lu/Vtg;)V
    invoke-direct { v3, v2, v1 }, Lu/OEg;-><init>(Lu/nJ;Lu/hl;)V
    new-instance v0, Lu/Fng;
    invoke-direct { v0, p0 }, Lu/Fng;-><init>(Lu/CiN;)V
    invoke-virtual { v3, v4, v0 }, Lu/OEg;->Rdu(Ljava/lang/Object;Lu/Vau;)V
    return-void
.end method