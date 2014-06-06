

//#include <zmq.hpp>
#include <zmq.h>
#include <math.h>

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

typedef float (*func_t)(float*);


float op_negate(float*v)
{
	return -*v;
}

float op_add(float*v)
{
	return v[0] + v[1];
}
float op_multiply(float*v)
{
	return v[0] * v[1];
}
float op_subtract(float*v)
{
	return v[0] - v[1];
}
float op_divide(float*v)
{
	return v[0] / v[1];
}
float op_power(float*v)
{
	return powf(v[0],v[1]);
}



main(int argc, const char* argv[])
{
	float parameters[2],	//max 2 parameters
			result;
	void*context;
	void*socket;
	const char*url;
	int rc;
	int op;
	zmq_msg_t msg;
	size_t size;
	func_t ops[OP_COUNT];
	
	ops[OP_NEGATE] = op_negate;
	ops[OP_ADD] = op_add;
	ops[OP_SUBTRACT] = op_subtract;
	ops[OP_MULTIPLY] = op_multiply;
	ops[OP_DIVIDE] = op_divide;
	ops[OP_POWER] = op_power;



	context = zmq_ctx_new();
	socket = zmq_socket(context, ZMQ_REP);
	url = "tcp://*:5555";
	rc = zmq_bind(socket,url);

	if (rc != 0)
	{
		printf("Fatal: Failed to bind socket to '%s'\n",url);
		return -1;
	}

	printf("Info: Now handling requests...\n",url);
	while (1)
	{
		zmq_msg_init(&msg);
		zmq_recvmsg(socket,&msg,0);
		result = 0.f;
		if (zmq_msg_more(&msg) && zmq_msg_size(&msg) == 4)
		{
			memcpy(&op,zmq_msg_data(&msg),4);
			zmq_recvmsg(socket,&msg,0);
			size = zmq_msg_size(&msg);
			memcpy(parameters,zmq_msg_data(&msg),min(size,8));
			if (op < 0 || op >= OP_COUNT)
			{
				printf("Warning: Received invalid instruction code %i\n",op);
				result = 0.f;
			}
			else
				result = ops[op](parameters);

		}
		else
			printf("Warning: Received invalid instruction\n");
		zmq_send(socket,&result,4,0);

		zmq_msg_close(&msg);
	}

	return 0;
}

