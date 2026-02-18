# Deploying to Render.com

This project includes a `Dockerfile` configured for deployment on Render.com.

## Prerequisites

- A [Render.com](https://render.com/) account.
- The project pushed to a GitHub or GitLab repository.
- A PostgreSQL database (e.g., from Render, Neon, or another provider).

## Deployment Steps

1.  **Create a New Web Service**
    - Go to the Render Dashboard.
    - Click **New +** -> **Web Service**.
    - Connect your repository.

2.  **Configure Service**
    - **Name**: Choose a name for your service.
    - **Runtime**: Render will automatically detect `Docker`.
    - **Region**: Choose a region close to your users (and database).

3.  **Environment Variables**
    Add the following environment variables in the "Environment" tab:

    | Variable               | Value                                                 |
    |------------------------|-------------------------------------------------------|
    | `SPRING_PROFILES_ACTIVE` | `deploy`                                              |
    | `JDBC_URI`             | `jdbc:postgresql://<host>:<port>/<database>?user=<user>&password=<password>` |

    > **Note:** The `JDBC_URI` format is standard JDBC. Ensure the database matches what's expected by `application-deploy.properties`.

4.  **Deploy**
    - Click **Create Web Service**.
    - Render will build the Docker image and deploy it.
    - The build logs will show the Maven build process.
    - Once deployed, the service will be available at your `.onrender.com` URL.

## Local Testing

You can test the Docker build locally:

```bash
docker build -t spring-ajax .
docker run -p 8080:8080 -e SPRING_PROFILES_ACTIVE=create spring-ajax
```
(Note: locally you might want to use the `create` or `default` profile if you don't have a remote DB set up for `deploy` profile variables).
