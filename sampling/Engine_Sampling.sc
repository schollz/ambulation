// CroneEngine_Sampling
Engine_Sampling : CroneEngine {

    //////// 1 ////////
    // we will initialize variables here
    // the only variable we need is one
    // to store the synth we create
    var synthSampler;

    // don't change this
    *new { arg context, doneCallback;
        ^super.new(context, doneCallback);
    }

    // alloc is where we will define things
    alloc {

        //////// 2 ////////
        // define the drone here!
        SynthDef("sampler", {
            arg out=0, bufnum=0, rate=1, rateLag=0.2, start=0, end=1, reset=0, t_trig=1,
            loops=1, amp=0.5;
            var snd,snd1,snd2,pos,pos2,frames,duration,env;
            var startA,endA,startB,endB,resetA,resetB,crossfade,aOrB;

            // latch to change trigger between the two
            aOrB=ToggleFF.kr(t_trig);
            startA=Latch.kr(start,aOrB);
            endA=Latch.kr(end,aOrB);
            resetA=Latch.kr(reset,aOrB);
            startB=Latch.kr(start,1-aOrB);
            endB=Latch.kr(end,1-aOrB);
            resetB=Latch.kr(reset,1-aOrB);
            crossfade=Lag.ar(K2A.ar(aOrB),0.05);

            rate = Lag.kr(rate,rateLag);
            rate = rate*BufRateScale.kr(bufnum);

            frames = BufFrames.kr(bufnum);
            duration = frames*(end-start)/rate.abs/s.sampleRate*loops;

            // envelope to clamp looping
            env=EnvGen.ar(
                Env.new(
                    levels: [0,1,1,0],
                    times: [0,duration-0.05,0.05],
                ),
                gate:t_trig,
            );

            pos=Phasor.ar(
                trig:aOrB,
                rate:rate,
                start:(((rate>0)*startA)+((rate<0)*endA))*frames,
                end:(((rate>0)*endA)+((rate<0)*startA))*frames,
                resetPos:(((rate>0)*resetA)+((rate<0)*endA))*frames,
            );
            snd1=BufRd.ar(
                numChannels:2,
                bufnum:bufnum,
                phase:pos,
                interpolation:4,
            );

            // add a second reader
            pos2=Phasor.ar(
                trig:(1-aOrB),
                rate:rate,
                start:(((rate>0)*startB)+((rate<0)*endB))*frames,
                end:(((rate>0)*endB)+((rate<0)*startB))*frames,
                resetPos:(((rate>0)*resetB)+((rate<0)*endB))*frames,
            );
            snd2=BufRd.ar(
                numChannels:2,
                bufnum:bufnum,
                phase:pos2,
                interpolation:4,
            );

            snd = (crossfade*snd1)+((1-crossfade)*snd2) * env * amp;

            Out.ar(out,snd)
        }).add;

        //////// 3 ////////
        // create the drone here!
        // it will run forever :)
        synthSampler = Array.fill(2,{arg i;
            Synth("sampler",target:context.xg);
        });

        //////// 4 ////////
        // define commands for the lua
        // script here

        // a load function to load samples
        this.addCommand("load","is", { arg msg;
            Buffer.read(s,msg[2],action:{
                arg buffer;
                synthSampler[msg[1]].set(\bufnum,buffer);
            });
        });

        // setting the position
        this.addCommand("pos","iff", { arg msg;
            synthSampler[msg[1]].set(
                \t_trig,1,
                \reset,msg[2],
                \start,msg[2],
                \end,msg[3],
            );
        });

        this.addCommand("rate","if", { arg msg;
            synthSampler[msg[1]].set(
                \rate,msg[2],
            );
        });


        this.addCommand("amp","if", { arg msg;
            synthSampler[msg[1]].set(
                \amp,msg[2],
            );
        });

    }


    free {
        //////// 5 ////////
        // free any variable we create
        // otherwise it won't ever stop!
        2.do({arg i; synthSampler[i].free});
    }
}
