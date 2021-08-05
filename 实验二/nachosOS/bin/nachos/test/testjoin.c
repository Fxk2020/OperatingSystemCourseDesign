#include "syscall.h"
#include "stdio.h"
#include "stdlib.h"

int main(int argc, char** argv)
{
	int i = 0;
	for(; i < 15; ++i)
	{
		//对exec和join等系统调用进行测试
		if(i == 3)
		{
			char* a[] = {"在这里测试了exec系统调用和join系统调用"};
			printf("child execute\n");
			int p = exec("echo.coff", 1, a);
			int b = -1;
			join(p, &b);
		}
		printf("father process %d\n", i);
	}
	return 0;
}
