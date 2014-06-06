

//#include <zmq.hpp>
#include <zmq.h>

enum op_t
{
	OP_NEGATE,
	OP_ADD,
	OP_SUBTRACT,
	OP_MULTIPLY,
	OP_DIVIDE,
	OP_POWER,

	OP_COUNT
};



void	* sock;


float op_call(int op, float x, float y, int numParam, char op_)
{
	float	result,
			pfield[2] = {x,y};

	zmq_send(sock,&op,4,ZMQ_SNDMORE);
	zmq_send(sock,pfield,4*numParam,0);
	zmq_recv(sock,&result,4,0);
	if (numParam == 2)
		printf("%.2f %c %.2f = %.2f\n",x,op_,y,result);
	else
		printf("%c %.2f = %.2f\n",op_,x,result);
	return result;
}


float fneg(float x)
{
	return op_call(OP_NEGATE,x,0,1,'-');
}

float fadd(float x, float y)
{
	return op_call(OP_ADD,x,y,2,'+');
}
float fmul(float x, float y)
{
	return op_call(OP_MULTIPLY,x,y,2,'*');
}
float fsub(float x, float y)
{
	return op_call(OP_SUBTRACT,x,y,2,'-');
}
float fdiv(float x, float y)
{
	return op_call(OP_DIVIDE,x,y,2,'/');
}
float fpow(float x, float y)
{
	return op_call(OP_POWER,x,y,2,'^');
}



main(int argc, const char* argv[])
{
	void*context;
	int rc;
	const char*url;
	float x,y,z,w;


	context = zmq_ctx_new();
	sock = zmq_socket(context, ZMQ_REQ);
	url = "tcp://localhost:5555";
	rc = zmq_connect(sock,url);

	if (rc != 0)
	{
		printf("Fatal: Failed to connect socket to '%s'\n",url);
		return -1;
	}

	x = 3;
	y = 4;
	z = 5;
	w = 6;


	fsub(
		fmul(
			fadd(
				fdiv(x,y),
				fdiv(y,x)
			),
			z
		),
		w
	);

	zmq_disconnect(sock,url);
	zmq_close(sock);
	zmq_ctx_destroy(context);

	system("PAUSE");
	return 0;
}

