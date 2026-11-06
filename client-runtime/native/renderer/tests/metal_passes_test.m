// SPDX-License-Identifier: GPL-3.0-only
#define GL_SILENCE_DEPRECATION
#include "../arc_scene_passes.h"
#import <Cocoa/Cocoa.h>
#import <Metal/Metal.h>
#include <OpenGL/gl3.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define CHECK(x) do { if(!(x)){fprintf(stderr,"Ordered pass check failed at %d: %s (%s)\n",__LINE__,#x,arc_scene_error(scene));return 1;} } while(0)
static const float vertices[]={-1,-1,0,0,0, 1,-1,0,1,0, 1,1,0,1,1, -1,-1,0,0,0, 1,1,0,1,1, -1,1,0,0,1};
static const uint8_t checker[]={255,255,255,255,255,255,255,0,255,255,255,0,255,255,255,255};
static const float identity[]={1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};
static const char *vertexSource="#version 150\nin vec3 position;in vec2 texcoord;uniform mat4 camera;uniform mat4 model;uniform vec4 tint;out vec2 uv;out vec4 color;void main(){gl_Position=camera*model*vec4(position,1);uv=texcoord;color=tint;}";
static const char *fragmentSource="#version 150\nin vec2 uv;in vec4 color;uniform sampler2D material;uniform float cutoff;out vec4 outputColor;void main(){vec4 c=texture(material,uv)*color;if(cutoff>=0&&c.a<=cutoff)discard;outputColor=c;}";

static GLuint shader(GLenum type,const char *source) {
    GLuint value=glCreateShader(type);glShaderSource(value,1,&source,NULL);glCompileShader(value);
    GLint valid=0;glGetShaderiv(value,GL_COMPILE_STATUS,&valid);
    if(!valid){char log[2048];glGetShaderInfoLog(value,sizeof(log),NULL,log);fprintf(stderr,"GL pass shader: %s\n",log);glDeleteShader(value);return 0;}
    return value;
}
static int reference(GLuint program,GLuint fbo,GLuint white,GLuint texture,const float *actors,const float *uniforms,
    const arc_scene_pipeline_state *states,const arc_scene_pass *passes,const arc_scene_command *commands,int width,int height,
    uint8_t *rgba,float *depth) {
    static const GLenum comparisons[]={GL_NEVER,GL_LESS,GL_EQUAL,GL_LEQUAL,GL_GREATER,GL_NOTEQUAL,GL_GEQUAL,GL_ALWAYS};
    static const GLenum factors[]={GL_ZERO,GL_ONE,GL_SRC_COLOR,GL_ONE_MINUS_SRC_COLOR,GL_DST_COLOR,GL_ONE_MINUS_DST_COLOR,
        GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA,GL_DST_ALPHA,GL_ONE_MINUS_DST_ALPHA};
    static const GLenum operations[]={GL_FUNC_ADD,GL_FUNC_SUBTRACT,GL_FUNC_REVERSE_SUBTRACT,GL_MIN,GL_MAX};
    glBindFramebuffer(GL_FRAMEBUFFER,fbo);glDisable(GL_DITHER);glDisable(GL_MULTISAMPLE);glDisable(GL_FRAMEBUFFER_SRGB);
    glUseProgram(program);glUniform1i(glGetUniformLocation(program,"material"),0);glActiveTexture(GL_TEXTURE0);
    for(int i=0;i<4;i++) {
        const arc_scene_pass *p=passes+i;const arc_scene_pipeline_state *s=states+p->pipeline_id-1;
        GLbitfield clear=0;glDisable(GL_SCISSOR_TEST);glColorMask(GL_TRUE,GL_TRUE,GL_TRUE,GL_TRUE);glDepthMask(GL_TRUE);
        if((p->load_actions&3)==ARC_CLEAR){glClearColor(p->clear_rgba[0],p->clear_rgba[1],p->clear_rgba[2],p->clear_rgba[3]);clear|=GL_COLOR_BUFFER_BIT;}
        if(((p->load_actions>>2)&3)==ARC_CLEAR){glClearDepth(p->clear_depth);clear|=GL_DEPTH_BUFFER_BIT;}
        if(clear)glClear(clear);
        glViewport(p->viewport_x,p->viewport_y,p->viewport_width,p->viewport_height);glDepthRange(p->min_depth,p->max_depth);
        if(p->flags&ARC_PASS_SCISSOR){glEnable(GL_SCISSOR_TEST);glScissor(p->scissor_x,p->scissor_y,p->scissor_width,p->scissor_height);}
        if(s->flags&ARC_PIPE_DEPTH_TEST)glEnable(GL_DEPTH_TEST);else glDisable(GL_DEPTH_TEST);
        glDepthFunc(comparisons[s->depth_compare]);glDepthMask((s->flags&ARC_PIPE_DEPTH_WRITE)!=0);
        if(s->flags&ARC_PIPE_BLEND)glEnable(GL_BLEND);else glDisable(GL_BLEND);
        glBlendFuncSeparate(factors[s->src_rgb],factors[s->dst_rgb],factors[s->src_alpha],factors[s->dst_alpha]);
        glBlendEquationSeparate(operations[s->rgb_operation],operations[s->alpha_operation]);
        glColorMask((s->color_mask&1)!=0,(s->color_mask&2)!=0,(s->color_mask&4)!=0,(s->color_mask&8)!=0);
        if(s->cull){glEnable(GL_CULL_FACE);glCullFace(s->cull==ARC_CULL_FRONT?GL_FRONT:GL_BACK);}else glDisable(GL_CULL_FACE);
        glFrontFace(s->winding==ARC_WIND_CCW?GL_CCW:GL_CW);
        const float *u=(const float *)((const uint8_t *)uniforms+p->uniform_offset);
        glUniformMatrix4fv(glGetUniformLocation(program,"camera"),1,GL_FALSE,u);glUniform1f(glGetUniformLocation(program,"cutoff"),u[16]);
        for(uint32_t j=p->first_command;j<p->first_command+p->command_count;j++) {
            arc_scene_command draw=commands[j];if(draw.mesh_id!=1||draw.material_id>1)return 1;
            glBindTexture(GL_TEXTURE_2D,draw.material_id?texture:white);
            for(uint32_t actor=draw.first_actor;actor<draw.first_actor+draw.actor_count;actor++) {
                glUniformMatrix4fv(glGetUniformLocation(program,"model"),1,GL_FALSE,actors+actor*20);
                glUniform4fv(glGetUniformLocation(program,"tint"),1,actors+actor*20+16);
                glDrawArrays(GL_TRIANGLES,draw.first_vertex,draw.vertex_count);
            }
        }
    }
    glPixelStorei(GL_PACK_ALIGNMENT,1);glReadPixels(0,0,width,height,GL_RGBA,GL_UNSIGNED_BYTE,rgba);
    glReadPixels(0,0,width,height,GL_DEPTH_COMPONENT,GL_FLOAT,depth);
    for(int y=0;y<height/2;y++)for(int x=0;x<width;x++) {
        size_t a=(size_t)y*width+x,b=(size_t)(height-1-y)*width+x;
        for(int c=0;c<4;c++){uint8_t old=rgba[a*4+c];rgba[a*4+c]=rgba[b*4+c];rgba[b*4+c]=old;}
        float old=depth[a];depth[a]=depth[b];depth[b]=old;
    }
    return glGetError()!=GL_NO_ERROR;
}

int main(int argc,const char **argv) {
    @autoreleasepool {
        void *scene=NULL;CHECK(argc==3);
        id<MTLDevice> device=MTLCreateSystemDefaultDevice();if(!device){puts("SKIP: No Metal device");return 77;}
        NSOpenGLPixelFormatAttribute attributes[]={NSOpenGLPFAOpenGLProfile,NSOpenGLProfileVersion3_2Core,NSOpenGLPFAColorSize,24,NSOpenGLPFAAlphaSize,8,0};
        NSOpenGLPixelFormat *format=[[NSOpenGLPixelFormat alloc]initWithAttributes:attributes];
        NSOpenGLContext *gl=[[NSOpenGLContext alloc]initWithFormat:format shareContext:nil];CHECK(gl);[gl makeCurrentContext];
        GLuint vao,vbo,fbo,color,depthTexture,textures[2];glGenVertexArrays(1,&vao);glBindVertexArray(vao);
        glGenBuffers(1,&vbo);glBindBuffer(GL_ARRAY_BUFFER,vbo);glBufferData(GL_ARRAY_BUFFER,sizeof(vertices),vertices,GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);glVertexAttribPointer(0,3,GL_FLOAT,GL_FALSE,20,(void *)0);
        glEnableVertexAttribArray(1);glVertexAttribPointer(1,2,GL_FLOAT,GL_FALSE,20,(void *)12);
        GLuint vs=shader(GL_VERTEX_SHADER,vertexSource),fs=shader(GL_FRAGMENT_SHADER,fragmentSource);CHECK(vs&&fs);
        GLuint program=glCreateProgram();glAttachShader(program,vs);glAttachShader(program,fs);
        glBindAttribLocation(program,0,"position");glBindAttribLocation(program,1,"texcoord");glBindFragDataLocation(program,0,"outputColor");glLinkProgram(program);
        GLint linked=0;glGetProgramiv(program,GL_LINK_STATUS,&linked);CHECK(linked);
        glGenTextures(2,textures);uint8_t white[]={255,255,255,255};
        for(int i=0;i<2;i++) {
            glBindTexture(GL_TEXTURE_2D,textures[i]);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
            glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,i?2:1,i?2:1,0,GL_RGBA,GL_UNSIGNED_BYTE,i?checker:white);
        }
        glGenFramebuffers(1,&fbo);glBindFramebuffer(GL_FRAMEBUFFER,fbo);glGenTextures(1,&color);glGenTextures(1,&depthTexture);
        glBindTexture(GL_TEXTURE_2D,color);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,64,48,0,GL_RGBA,GL_UNSIGNED_BYTE,NULL);
        glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,color,0);
        glBindTexture(GL_TEXTURE_2D,depthTexture);glTexImage2D(GL_TEXTURE_2D,0,GL_DEPTH_COMPONENT32F,64,48,0,GL_DEPTH_COMPONENT,GL_FLOAT,NULL);
        glFramebufferTexture2D(GL_FRAMEBUFFER,GL_DEPTH_ATTACHMENT,GL_TEXTURE_2D,depthTexture,0);
        glDrawBuffer(GL_COLOR_ATTACHMENT0);glReadBuffer(GL_COLOR_ATTACHMENT0);CHECK(glCheckFramebufferStatus(GL_FRAMEBUFFER)==GL_FRAMEBUFFER_COMPLETE);
        NSError *error=nil;NSString *source=[NSString stringWithContentsOfFile:[NSString stringWithUTF8String:argv[1]] encoding:NSUTF8StringEncoding error:&error];CHECK(source);
        scene=arc_scene_create(4,80,20,1024);CHECK(scene&&arc_scene_pass_abi()==1);
        CHECK(arc_scene_mesh(scene,1,vertices,sizeof(vertices))==ARC_OK);CHECK(arc_scene_material(scene,1,2,2,checker,sizeof(checker))==ARC_OK);
        float actors[80]={0},uniforms[256]={0};const float tints[4][4]={{1,0,0,1},{0,1,0,1},{0,0,1,.5f},{.25f,.125f,.375f,.5f}};
        const float zs[]={.5f,-.5f,0,1};
        for(int i=0;i<4;i++){memcpy(actors+i*20,identity,sizeof(identity));actors[i*20+14]=zs[i];memcpy(actors+i*20+16,tints[i],16);memcpy(uniforms+i*64,identity,sizeof(identity));uniforms[i*64+16]=i==1?.5f:-1;}
        CHECK(arc_scene_write(scene,0,actors,sizeof(actors))==ARC_OK);
        uint8_t glRgba[64*48*4],metalRgba[sizeof(glRgba)],baseRgba[sizeof(glRgba)];
        float glDepth[64*48],metalDepth[64*48],baseDepth[64*48];
        uint64_t changed[5]={0};int maximum=0;uint64_t depthDifferences=0;
        for(int variant=0;variant<6;variant++) {
            arc_scene_pipeline_state states[4]={
                {3,ARC_COMPARE_LESS,ARC_CULL_BACK,ARC_WIND_CCW,1,0,1,0,0,0,15,0},
                {3,ARC_COMPARE_LESS,ARC_CULL_NONE,ARC_WIND_CCW,1,0,1,0,0,0,15,0},
                {5,ARC_COMPARE_LEQUAL,ARC_CULL_NONE,ARC_WIND_CCW,ARC_BLEND_SRC_ALPHA,ARC_BLEND_INV_SRC_ALPHA,1,ARC_BLEND_INV_SRC_ALPHA,0,0,15,0},
                {4,ARC_COMPARE_ALWAYS,ARC_CULL_NONE,ARC_WIND_CCW,1,ARC_BLEND_INV_SRC_ALPHA,1,ARC_BLEND_INV_SRC_ALPHA,0,0,15,0}};
            if(variant==1)states[2].flags|=ARC_PIPE_DEPTH_WRITE;
            if(variant==2)states[3].src_rgb=ARC_BLEND_SRC_ALPHA;
            if(variant==5)states[3].color_mask=5;
            for(int i=0;i<4;i++)CHECK(arc_scene_pipeline_state_put(scene,i+1,source.UTF8String,"passVertex","passFragment",states+i)==ARC_OK);
            arc_scene_command commands[4];arc_scene_pass passes[4];
            for(int i=0;i<4;i++) {
                commands[i]=(arc_scene_command){1,i==1?1:0,0,6,(uint32_t)i,1};
                passes[i]=(arc_scene_pass){(uint32_t)i+1,(uint32_t)i,1,(uint32_t)i*256,80,i?0:5,3,0,0,0,64,48,0,0,0,0,{0,0,0,1},1,0,1,0};
            }
            passes[3].viewport_x=3;passes[3].viewport_y=4;passes[3].viewport_width=54;passes[3].viewport_height=36;
            passes[3].flags=ARC_PASS_SCISSOR;passes[3].scissor_x=5;passes[3].scissor_y=7;passes[3].scissor_width=19;passes[3].scissor_height=23;
            if(variant==3)passes[3].flags=0;
            if(variant==4){arc_scene_pass p=passes[2];passes[2]=passes[3];passes[3]=p;passes[2].first_command=2;passes[3].first_command=3;arc_scene_command c=commands[2];commands[2]=commands[3];commands[3]=c;}
            CHECK(reference(program,fbo,textures[0],textures[1],actors,uniforms,states,passes,commands,64,48,glRgba,glDepth)==0);
            CHECK(arc_scene_submit_passes(scene,64,48,uniforms,sizeof(uniforms),passes,4,commands,4)==ARC_OK);
            CHECK(arc_scene_read_rgba(scene,metalRgba,sizeof(metalRgba))==ARC_OK);CHECK(arc_scene_read_depth(scene,metalDepth,sizeof(metalDepth))==ARC_OK);
            GLint currentProgram=0,currentFbo=0,currentVao=0;
            glGetIntegerv(GL_CURRENT_PROGRAM,&currentProgram);glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING,&currentFbo);glGetIntegerv(GL_VERTEX_ARRAY_BINDING,&currentVao);
            CHECK([NSOpenGLContext currentContext]==gl&&(GLuint)currentProgram==program&&(GLuint)currentFbo==fbo&&(GLuint)currentVao==vao);
            for(size_t i=0;i<sizeof(metalRgba);i++) {
                int delta=abs((int)metalRgba[i]-glRgba[i]);if(delta>maximum)maximum=delta;
                if(delta>1)fprintf(stderr,"RGBA variant=%d x=%zu y=%zu channel=%zu GL=%u Metal=%u\n",variant,(i/4)%64,i/(64*4),i%4,glRgba[i],metalRgba[i]);
                CHECK(delta<=1);if(variant&&metalRgba[i]!=baseRgba[i])changed[variant-1]++;
            }
            for(size_t i=0;i<64*48;i++) {
                if(memcmp(metalDepth+i,glDepth+i,4)) {
                    uint32_t expected,actual;memcpy(&expected,glDepth+i,4);memcpy(&actual,metalDepth+i,4);
                    depthDifferences++;fprintf(stderr,"Depth variant=%d x=%zu y=%zu GL=%08x Metal=%08x\n",variant,i%64,i/64,expected,actual);CHECK(0);
                }
                if(variant&&memcmp(metalDepth+i,baseDepth+i,4))changed[variant-1]++;
            }
            if(!variant){memcpy(baseRgba,metalRgba,sizeof(baseRgba));memcpy(baseDepth,metalDepth,sizeof(baseDepth));}
            else CHECK(changed[variant-1]>0);
            uint64_t submissions=arc_scene_query(scene,0);passes[1].uniform_offset=4;
            CHECK(arc_scene_submit_passes(scene,64,48,uniforms,sizeof(uniforms),passes,4,commands,4)==ARC_INVALID&&arc_scene_query(scene,0)==submissions);
            CHECK(arc_scene_read_rgba(scene,glRgba,sizeof(glRgba))==ARC_OK&&!memcmp(glRgba,metalRgba,sizeof(glRgba)));
        }
        CHECK(arc_scene_query(scene,0)==6&&arc_scene_query(scene,8)==24&&arc_scene_query(scene,7)==4);
        CHECK(arc_scene_reset(scene)==ARC_OK&&arc_scene_query(scene,7)==0&&arc_scene_query(scene,9)==0);
        arc_scene_destroy(scene);scene=NULL;
        glUseProgram(0);glBindFramebuffer(GL_FRAMEBUFFER,0);glBindVertexArray(0);glBindBuffer(GL_ARRAY_BUFFER,0);glBindTexture(GL_TEXTURE_2D,0);
        glDeleteProgram(program);glDeleteShader(vs);glDeleteShader(fs);glDeleteBuffers(1,&vbo);glDeleteVertexArrays(1,&vao);
        glDeleteTextures(2,textures);glDeleteTextures(1,&color);glDeleteTextures(1,&depthTexture);glDeleteFramebuffers(1,&fbo);CHECK(glGetError()==GL_NO_ERROR);
        CHECK(!glIsProgram(program)&&!glIsShader(vs)&&!glIsShader(fs)&&!glIsBuffer(vbo)&&!glIsVertexArray(vao)
            &&!glIsTexture(textures[0])&&!glIsTexture(textures[1])&&!glIsTexture(color)&&!glIsTexture(depthTexture)&&!glIsFramebuffer(fbo));
        [NSOpenGLContext clearCurrentContext];
        FILE *report=fopen(argv[2],"w");CHECK(report);
        fprintf(report,"{\"passed\":true,\"sharedPackedPasses\":true,\"cases\":6,\"actualMetalPasses\":24,\"rgbaBytesCompared\":73728,\"rawDepthWordsCompared\":18432,\"maxRgbaDifference\":%d,\"depthDifferences\":%llu,\"negativeControlChanges\":[%llu,%llu,%llu,%llu,%llu],\"worldOrSkikoQualified\":false,\"performanceClaim\":false}\n",maximum,(unsigned long long)depthDifferences,(unsigned long long)changed[0],(unsigned long long)changed[1],(unsigned long long)changed[2],(unsigned long long)changed[3],(unsigned long long)changed[4]);fclose(report);
        puts("Metal/OpenGL ordered pass full RGBA/depth, cull, cutout, alpha, premultiplied HUD, scissor, uniforms, negative controls and reset passed");
        return 0;
    }
}
