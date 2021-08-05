# 实验二

### 编译交叉环境

这一部分十分重要，如果做不出来，实验三也不好做，做出来实验三就很容易了。具体实现见MIPS环境配置文件夹。

### 彩票调度

当测试彩票调度时需要将

```java
Kernel.kernel = nachos.userprog.UserKernel
```

变为

```java
Kernel.kernel = nachos.threads.ThreadedKernel
```

