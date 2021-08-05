#include "syscall.h"
#include "stdio.h"
#include "stdlib.h"

int main(int argc, char** argv)
{
    char *filename="Nachos_mips.txt";
	creat(filename);
	printf("calling 'create(filename)'...");
	printf("done ! \n");
}
