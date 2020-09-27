# see https://developers.google.com/android-publisher/api-ref/rest
# see https://github.com/googleapis/google-api-python-client/blob/master/docs/oauth-server.md#preparing-to-make-an-authorized-api-call
# see https://github.com/googlesamples/android-play-publisher-api/blob/master/v3/python/basic_upload_apks.py

# pip install google-auth google-auth-httplib2 google-api-python-client oauth2client

import os
import googleapiclient.discovery
from oauth2client.service_account import ServiceAccountCredentials

TRACK = 'beta'
SERVICE_ACCOUNT_EMAIL = os.environ['GOOGLEPLAY_ACCT_EMAIL']
KEY_PATH = 'releases/google-play-key.p12'

PACKAGE_NAME = 'com.ubergeek42.WeechatAndroid.dev'
APK_PATH = "app/build/outputs/apk/devrelease/app-devrelease.apk"


def credentials():
    return ServiceAccountCredentials.from_p12_keyfile(
            SERVICE_ACCOUNT_EMAIL,
            KEY_PATH,
            scopes=['https://www.googleapis.com/auth/androidpublisher'])


def upload():
    with googleapiclient.discovery.build('androidpublisher', 'v2',
                                         credentials=credentials()) as service:
        edit_request = service.edits().insert(body={}, packageName=PACKAGE_NAME)
        result = edit_request.execute()
        edit_id = result['id']

        raise Exception("boo")

        apk_response = service.edits().apks().upload(
                editId=edit_id,
                packageName=PACKAGE_NAME,
                media_body=APK_PATH).execute()

        version_code = apk_response['versionCode']

        print(f"Version code {version_code} has been uploaded")

        track_response = service.edits().tracks().update(
            editId=edit_id,
            track=TRACK,
            packageName=PACKAGE_NAME,
            body={
                "releases": [
                    {
                        "versionCodes": [
                            str(version_code)
                        ],
                        "status": "completed"
                    }
                ]
            }).execute()

        print(f"Track {track_response['track']} is set with releases: {track_response['releases']}")

        commit_request = service.edits().commit(editId=edit_id, packageName=PACKAGE_NAME).execute()

        print(f"Edit {commit_request['id']} has been committed")


if __name__ == '__main__':
    # if os.environ.get('TRAVIS_BRANCH', 'undefined') != 'master':
    #     raise Exception("Won't publish play store app for any branch except master")

    if os.environ.get('TRAVIS_PULL_REQUEST', None) != "false":
        raise Exception("Won't publish play store app for pull requests")

    upload()
