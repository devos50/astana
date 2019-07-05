.class public LIsolated;
.super Ljava/lang/Object;

.method public static main([Ljava/lang/String;)V
    .registers 20

    const-string v0, "\u000f\u001b\r\u0017#\u001d\u0011\n\u0019$\u0007\u0012\u0010\u0014\u0005\r\u0012\u0010\u001b\u000b\u000c}}{\u0008y\u0002uv"
    const/4 v12, 2
    const/16 v1, 255
    const/16 v4, 205
    invoke-static { v0, v1, v4, v12 }, Liiiiii/ljlljl;->b0411\u04110411\u0411\u0411\u0411\u04110411\u041104110411(Ljava/lang/String;CCC)Ljava/lang/String;
    move-result-object v1

    sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream;
    invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println(Ljava/lang/String;)V

    return-void
.end method