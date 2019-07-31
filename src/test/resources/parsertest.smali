.class public Lcfg;
.super Ljava/lang/Object;

# String declaration
.method public static method1([Ljava/lang/String;)V
  .locals 15
    const/4 v1, 2
    const/4 v2, 1
    const-string v3, "test"
    return-void
.end method

# String declaration + decryption method
.method public static method2([Ljava/lang/String;)V
  .locals 15
    const-string v3, "test"
    invoke-static { v3 }, Lu/NS;->R(Ljava/lang/String;)Ljava/lang/String;
    move-result-object v3
    return-void
.end method

# String declaration + decryption method + try/catch
.method public static method3([Ljava/lang/String;)V
  .catch Ljava/lang/Exception; { :L0 .. :L1 } :L2
  .locals 15
    const-string v3, "test"
    :L0
      const/4 v0, 1
    :L1
      return-void
    :L2
      move-exception v2
      invoke-static { v3 }, Lu/NS;->R(Ljava/lang/String;)Ljava/lang/String;
      move-result-object v3
      throw v2
.end method

# String declaration + decryption method + if statement
.method public static method4([Ljava/lang/String;)V
  .locals 15
    const/4 v0, 1
    const/4 v1, 2
    const-string v3, "test"
  :L0
    if-eqz v1, :L1
    sub-int/2addr v1, v0
    goto :L0
  :L1
    invoke-static { v3 }, Lu/NS;->R(Ljava/lang/String;)Ljava/lang/String;
    move-result-object v3
    return-void
.end method

# Empty string declaration
.method public static method5([Ljava/lang/String;)V
  .locals 15
    const/4 v1, 2
    const/4 v2, 1
    const-string v3, ""
    return-void
.end method

# String declaration + decryption method (without conditionals)
.method public static method6([Ljava/lang/String;)V
  .locals 15
    const-string v2, "ksxz'EF*y\u0002yz"
    const/16 v1, -29903
    invoke-static { }, Lu/jYu;->l()I
    move-result v0
    xor-int/2addr v0, v1
    int-to-short v0, v0
    invoke-static { v2, v0 }, Lu/NS;->n(Ljava/lang/String;S)Ljava/lang/String;
    move-result-object v0
    return-void
.end method

# String declaration + decryption method (for-loop)
.method public static method7([Ljava/lang/string;)V
  .locals 15
    const-string v4, "UIH"
    const/16 v3, 29764
    const/16 v1, 1626
    invoke-static { }, Lu/MD;->B()I
    move-result v0
    xor-int/2addr v0, v3
    int-to-short v8, v0
    invoke-static { }, Lu/MD;->B()I
    move-result v0
    xor-int/2addr v0, v1
    int-to-short v7, v0
    invoke-virtual { v4 }, Ljava/lang/String;->length()I
    move-result v0
    new-array v6, v0, [I
    new-instance v5, Lu/xFN;
    invoke-direct { v5, v4 }, Lu/xFN;-><init>(Ljava/lang/String;)V
    const/4 v4, 0
  :L0
    invoke-virtual { v5 }, Lu/xFN;->SJu()Z
    move-result v0
    if-eqz v0, :L1
    invoke-virtual { v5 }, Lu/xFN;->QJu()I
    move-result v0
    invoke-static { v0 }, Lu/Xju;->g(I)Lu/Xju;
    move-result-object v3
    invoke-virtual { v3, v0 }, Lu/Xju;->rPu(I)I
    move-result v1
    add-int v0, v8, v4
    add-int/2addr v0, v1
    add-int/2addr v0, v7
    invoke-virtual { v3, v0 }, Lu/Xju;->ePu(I)I
    move-result v0
    aput v0, v6, v4
    add-int/lit8 v4, v4, 1
    goto :L0
  :L1
    new-instance v1, Ljava/lang/String;
    const/4 v0, 0
    invoke-direct { v1, v6, v0, v4 }, Ljava/lang/String;-><init>([III)V
    return-void
.end method

# String declaration + decryption method (with useless conditional)
.method public static method8([Ljava/lang/String;)V
  .locals 15
    const-string v2, "ksxz'EF*y\u0002yz"
    const/16 v1, -29903
    const/16 v3, 1234
    if-eqz v3, :L1
    const v0, 42
  :L1
    invoke-static { }, Lu/jYu;->l()I
    move-result v0
    xor-int/2addr v0, v1
    int-to-short v0, v0
    invoke-static { v2, v0 }, Lu/NS;->n(Ljava/lang/String;S)Ljava/lang/String;
    move-result-object v0
    return-void
.end method