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
