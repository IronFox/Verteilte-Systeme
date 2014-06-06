#define _CRT_SECURE_NO_WARNINGS

#include <zmq.h>
#include "zmq.hpp"
#include <iostream>
#include <ctime>

int main(int argc, const char* argv[])
{
	try
	{
		zmq::context_t context;
		zmq::socket_t socket(context, ZMQ_PUB);

		socket.bind("tcp://*:5556");

		std::cout << "Now serving time..."<<std::endl;
		for (;;)
		{
			time_t t = time(NULL);
			struct tm *lt = localtime(&t);
			unsigned char time[3] = {lt->tm_hour,lt->tm_min,lt->tm_sec};
			socket.send(time,3,0);

			Sleep(100);
		}
	}
	catch (...)
	{
		std::cout << "something bad has happend with zmq. shutting down."<<std::endl;
	}

	return 0;
}

