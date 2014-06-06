#include <zmq.h>
#include "zmq.hpp"
#include <iostream>
#include <ctime>
#include "glut/include/GL/glut.h"
#include <thread>
#pragma comment(lib,"freeglut.lib")
#include "model_obj.h"

GLint	windowWidth = 1024,
		windowHeight = 768;
float	viewDistance = 2.f;

float	minutesAngle = 0.f,
		secondsAngle = 0.f,
		hoursAngle = 0.f;

bool	renderClock = true;


zmq::context_t context;
zmq::socket_t	notificationClient(context,ZMQ_PAIR);

const char* inproc = "inproc://clockUpdates";


void TimeFunc()
{
	try
	{
		zmq::socket_t	notificationSocket(context,ZMQ_PAIR);
		if (renderClock)
			notificationSocket.connect(inproc);
		zmq::socket_t socket(context, ZMQ_SUB);

		socket.connect("tcp://localhost:5556");
		socket.setsockopt(ZMQ_SUBSCRIBE,NULL,0);
		//const char *sub = "\x0D\x30";
		//socket.setsockopt(ZMQ_SUBSCRIBE,sub,strlen(sub));

		unsigned char prevTime[3] = {0,0,0};
		for (;;)
		{
			unsigned char time[3];
			socket.recv(time,3,0);
			if (time[0] != prevTime[0] || time[1] != prevTime[1] || time[2] != prevTime[2])
			{
				memcpy(prevTime,time,3);
				printf("\r%.2u:%.2u:%.2u",time[0],time[1],time[2]);

				float secRel = float((time[2])) / 60.f;
				float minRel = (float((time[1])) + secRel) / 60.f;
				hoursAngle = (float((time[0]) %12) + minRel) / 12.f * 360.f;
				minutesAngle = minRel * 360.f;
				secondsAngle = secRel * 360.f;

				if (renderClock)
				{
					bool msg = true;
					notificationSocket.send(&msg,1,0);
				}
			}
		}
	}
	catch (...)
	{
		std::cout << "something bad has happend with zmq. shutting down."<<std::endl;
	}
}




float g_fTeapotAngle2 = 0.f;


ModelOBJ	clockBase,clockDigits, clockSeconds,clockMinutes,clockHours;




void Draw(const ModelOBJ&obj)
{
    const ModelOBJ::Mesh *pMesh = 0;
    const ModelOBJ::Material *pMaterial = 0;
    const ModelOBJ::Vertex *pVertices = 0;
    //ModelTextures::const_iterator iter;
    if (obj.hasPositions())
    {
        glEnableClientState(GL_VERTEX_ARRAY);
        glVertexPointer(3, GL_FLOAT, obj.getVertexSize(),
            obj.getVertexBuffer()->position);
    }

    if (obj.hasTextureCoords())
    {
        glEnableClientState(GL_TEXTURE_COORD_ARRAY);
        glTexCoordPointer(2, GL_FLOAT, obj.getVertexSize(),
            obj.getVertexBuffer()->texCoord);
    }

    if (obj.hasNormals())
    {
        glEnableClientState(GL_NORMAL_ARRAY);
        glNormalPointer(GL_FLOAT, obj.getVertexSize(),
            obj.getVertexBuffer()->normal);
    }

    for (int i = 0; i < obj.getNumberOfMeshes(); ++i)
    {
        pMesh = &obj.getMesh(i);
        pMaterial = pMesh->pMaterial;
        pVertices = obj.getVertexBuffer();

        //glMaterialfv(GL_FRONT_AND_BACK, GL_AMBIENT, pMaterial->ambient);
        //glMaterialfv(GL_FRONT_AND_BACK, GL_DIFFUSE, pMaterial->diffuse);
        //glMaterialfv(GL_FRONT_AND_BACK, GL_SPECULAR, pMaterial->specular);
        //glMaterialf(GL_FRONT_AND_BACK, GL_SHININESS, pMaterial->shininess * 128.0f);

        //if (g_enableTextures)
        //{
        //    iter = g_modelTextures.find(pMaterial->colorMapFilename);

        //    if (iter == g_modelTextures.end())
        //    {
        //        glDisable(GL_TEXTURE_2D);
        //    }
        //    else
        //    {
        //        glEnable(GL_TEXTURE_2D);
        //        glBindTexture(GL_TEXTURE_2D, iter->second);
        //    }
        //}
        //else
        {
            glDisable(GL_TEXTURE_2D);
        }


        glDrawElements(GL_TRIANGLES, pMesh->triangleCount * 3, GL_UNSIGNED_INT,
            obj.getIndexBuffer() + pMesh->startIndex);

    }
    if (obj.hasNormals())
        glDisableClientState(GL_NORMAL_ARRAY);

    if (obj.hasTextureCoords())
        glDisableClientState(GL_TEXTURE_COORD_ARRAY);

    if (obj.hasPositions())
        glDisableClientState(GL_VERTEX_ARRAY);
}




float scale = 0.003f;

void ScaleBackground(float color[4])
{
	color[0] *= 0.75f;
	color[1] *= 0.75f;
	color[2] *= 0.75f;
}

void RenderObjects(void)
{
	float colorBronzeDiff[4] = { 0.6f, 0.5f, 0.2f, 1.0f };
	float colorBronzeSpec[4] = { 1.1f, 1.0f, 0.4f, 1.0f };
	
	float colorDigitsDiff[4] = { 0.6f, 0.5f, 0.4f, 1.0f };
	float colorDigitsSpec[4] = { 1.1f, 1.1f, 0.9f, 1.0f };

	ScaleBackground(colorBronzeDiff);
	ScaleBackground(colorBronzeSpec);
	ScaleBackground(colorDigitsDiff);
	ScaleBackground(colorDigitsSpec);

	float colorHandsDiff[4] = { 0.6f, 0.6f, 0.6f, 1.0f };
	float colorHandsSpec[4] = { 1.1f, 1.1f, 1.1f, 1.0f };

	float colorHoursDiff[4] = { 0.6f, 0.6f, 0.6f, 1.0f };
	float colorHoursSpec[4] = { 1.1f, 1.1f, 1.1f, 1.0f };

	float colorSecondsDiff[4] = { 0.9f, 0.1f, 0.1f, 1.0f };
	float colorSecondsSpec[4] = { 1.1f, 0.7f, 0.7f, 1.0f };

	float colorBlue[4]       = { 0.0f, 0.2f, 1.0f, 1.0f };
	float colorNone[4]       = { 0.0f, 0.0f, 0.0f, 0.0f };
	glEnable(GL_NORMALIZE);
	glMatrixMode(GL_MODELVIEW);
	glPushMatrix();
		// Main object (cube) ... transform to its coordinates, and render
		//glRotatef(15, 1, 0, 0);
		//glRotatef(45, 0, 1, 0);
		//glRotatef(145.f, 0, 0, 1);
		//glMaterialfv(GL_FRONT, GL_DIFFUSE, colorBlue);
		//glMaterialfv(GL_FRONT, GL_SPECULAR, colorNone);
		//glColor4fv(colorBlue);
		glPushMatrix();
			//glTranslatef(2, 0, 0);
			glRotatef(180.f,0,0,1);
			glRotatef(110, 1, 0, 0);
			glRotatef(110, 0, 0, 1);
			//glRotatef(g_fTeapotAngle2, 1, 1, 0);
			glMaterialfv(GL_FRONT, GL_DIFFUSE, colorBronzeDiff);
			glMaterialfv(GL_FRONT, GL_SPECULAR, colorBronzeSpec);
			glMaterialf(GL_FRONT, GL_SHININESS, 10.0);
			glColor4fv(colorBronzeDiff);
			//glutSolidTeapot(0.3);
			glScalef(scale,scale,scale);
			Draw(clockBase);

			glMaterialfv(GL_FRONT, GL_DIFFUSE, colorDigitsDiff);
			glMaterialfv(GL_FRONT, GL_SPECULAR, colorDigitsSpec);

			Draw(clockDigits);

			glMaterialfv(GL_FRONT, GL_DIFFUSE, colorHandsDiff);
			glMaterialfv(GL_FRONT, GL_SPECULAR, colorHandsSpec);
			glPushMatrix();
				glRotatef(minutesAngle,1,0,0);

				Draw(clockMinutes);
			glPopMatrix();
			glMaterialfv(GL_FRONT, GL_DIFFUSE, colorHoursDiff);
			glMaterialfv(GL_FRONT, GL_SPECULAR, colorHoursSpec);
			glPushMatrix();
				glRotatef(hoursAngle,1,0,0);

				Draw(clockHours);
			glPopMatrix();

			glMaterialfv(GL_FRONT, GL_DIFFUSE, colorSecondsDiff);
			glMaterialfv(GL_FRONT, GL_SPECULAR, colorSecondsSpec);

			glPushMatrix();
				glRotatef(secondsAngle,1,0,0);

				Draw(clockSeconds);
			glPopMatrix();
		glPopMatrix(); 
	glPopMatrix();
}
void Display()
{
	glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
	glLoadIdentity();
	gluLookAt(0, 0, -viewDistance, 0, 0, -1, 0, 1, 0);
	RenderObjects();
	glutSwapBuffers();
}
void Reshape(GLint width, GLint height)
{
	windowWidth = width;
	windowHeight = height;
	glViewport(0, 0, windowWidth, windowHeight);
	glMatrixMode(GL_PROJECTION);
	glLoadIdentity();
	gluPerspective(65.0, (float)windowWidth / windowHeight, 0.1f, 1000.f);
	glMatrixMode(GL_MODELVIEW);
}
void InitGraphics(void)
{
	glEnable(GL_DEPTH_TEST);
	glDepthFunc(GL_LESS);
	glShadeModel(GL_SMOOTH);
	glEnable(GL_LIGHTING);
	glEnable(GL_LIGHT0);
	// Create texture for cube; load marble texture from file and bind it
	//pTextureImage = read_texture("marble.rgb", &width, &height, &nComponents);
	//glBindTexture(GL_TEXTURE_2D, TEXTURE_ID_CUBE);
	//gluBuild2DMipmaps(GL_TEXTURE_2D,     // texture to specify
	//					GL_RGBA,           // internal texture storage format
	//					width,             // texture width
	//					height,            // texture height
	//					GL_RGBA,           // pixel format
	//					GL_UNSIGNED_BYTE,	// color component format
	//					pTextureImage);    // pointer to texture image
	//glTexParameterf (GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
	//glTexParameterf (GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER,
	//				GL_LINEAR_MIPMAP_LINEAR);
	//glTexEnvf (GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL_MODULATE);
}


void AnimateScene()
{
	zmq::pollitem_t items[] = 
	{
		{ notificationClient, 0, ZMQ_POLLIN, 0 },
	};
	zmq_poll(items,1,1);
	if (items[0].revents & ZMQ_POLLIN)
	{
		bool var;
		notificationClient.recv(&var,1,0);
		glutPostRedisplay(); // Force redraw
	}
}


#define MENU_EXIT 0
void SelectFromMenu(int idCommand)
{
	switch (idCommand)
	{
		case MENU_EXIT:
			exit (0);
		break;
	}
	// Almost any menu selection requires a redraw
	glutPostRedisplay();
}

int BuildPopupMenu (void)
{
	int menu;
	menu = glutCreateMenu (SelectFromMenu);
	glutAddMenuEntry ("Exit\tEsc", MENU_EXIT);
	return menu;
}

int main(int argc, char* argv[])
{
	if (renderClock)
	{
		std::cout << "reading resources..."<<std::endl;
		#define LOAD(DATA)	std::cout << #DATA << ".obj..."<<std::endl; DATA.import(#DATA".obj");
		LOAD(clockBase)
		LOAD(clockDigits)
		LOAD(clockSeconds)
		LOAD(clockMinutes)
		LOAD(clockHours)

		notificationClient.bind(inproc);

		glutInit (&argc, argv);
		glutInitWindowSize (windowWidth, windowHeight);
		glutInitDisplayMode(GLUT_DOUBLE | GLUT_RGBA | GLUT_DEPTH | GLUT_MULTISAMPLE);
		glEnable(GL_MULTISAMPLE);
		glutCreateWindow ("Clock");
		InitGraphics();
		glutDisplayFunc (Display);
		glutReshapeFunc (Reshape);
		//glutMouseFunc (MouseButton);
		//glutMotionFunc (MouseMotion);
		glutIdleFunc (AnimateScene);
		BuildPopupMenu ();
		glutAttachMenu (GLUT_RIGHT_BUTTON);
		//#ifdef _WIN32
		//	last_idle_time = GetTickCount();
		//#else
		//	gettimeofday (&last_idle_time, NULL);
		//#endif

		std::thread readThread(TimeFunc);
		glutMainLoop ();
	}
	else
		TimeFunc();


	return 0;
}

