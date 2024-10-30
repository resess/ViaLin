import os
import time

files =[
"backflash",
"beita_com_beita_contact",
"cajino_baidu",
"chat_hook",
"chulia",
"death_ring_materialflow",
"dsencrypt_samp",
"exprespam",
"fakeappstore",
"fakebank_android_samp",
"fakedaum",
"fakemart",
"fakeplay",
"faketaobao",
"godwon_samp",
"hummingbad_android_samp",
"jollyserv",
"overlay_android_samp",
"overlaylocker2_android_samp",
"phospy",
"proxy_samp",
"remote_control_smack",
"repane",
"roidsec",
"samsapo",
"save_me",
"scipiex",
"slocker_android_samp",
"sms_google",
"sms_send_locker_qqmagic",
"smssend_packageInstaller",
"smssilience_fake_vertu",
"smsstealer_kysn_assassincreed_android_samp",
"stels_flashplayer_android_update",
"tetus",
"the_interview_movieshow",
"threatjapan_uracto",
"vibleaker_android_samp",
"xbot_android_samp",
]

# os.system("rm debug.log")
for f in files:
    # os.system(f"python3 extract_path.py /data/khaledea/2023_info_path_legitimacy/code/src/taintbench_analysis_vialin/outdir/results_vialin/{f}.log none /data/khaledea/ViaLin/framework_classes/ /data/khaledea/2023_info_path_legitimacy/code/src/taintbench_analysis_vialin/outdir/results/ >> debug.log")
    # time.sleep(1)
    print(f"{f}")
    os.system(f"ls /data/khaledea/2023_info_path_legitimacy/code/src/taintbench_analysis_vialin/outdir/results/{f}/paths/ | wc -l")