#include "syscall.h"
#include "stdio.h"
#include "stdlib.h"

int main(int argc, char** argv)
{
	int i = 0;
	for(; i < 15; ++i)
	{
		if(i == 3)
		{
			char* a[] = {"chang"};
			printf("child execute\n");
			int p = exec("echo.coff", 1, a);
			int b = -1;
			join(p, &b);
		}
		printf("father process %d\n", i);
	}
	return 0;
}
